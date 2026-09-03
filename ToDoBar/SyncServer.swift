import Foundation
import Network
import Combine

final class SyncServer: ObservableObject {
    @Published private(set) var status = "正在启动"
    @Published private(set) var pairedDeviceName: String?
    let pairing = PairingStore()
    private let store: TodoStore
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "com.chenxiaocai.ToDoBarSync.listener")

    init(store: TodoStore) {
        self.store = store
        pairedDeviceName = pairing.state.deviceName
    }

    var qrString: String? { pairing.qrString }

    func start() {
        do {
            let listener = try NWListener(using: .tcp)
            listener.service = NWListener.Service(name: "ToDoBar Sync", type: "_todobar-sync._tcp", txtRecord: NWTXTRecord(["server": pairing.state.serverID]))
            listener.newConnectionHandler = { [weak self] in self?.accept($0) }
            listener.stateUpdateHandler = { [weak self] state in
                Task { @MainActor in self?.status = String(describing: state) }
            }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            status = "启动失败：\(error.localizedDescription)"
        }
    }

    func stop() { listener?.cancel() }
    func revoke() { pairing.revoke(); pairedDeviceName = nil; restart() }

    private func restart() { stop(); listener = nil; start() }

    private func accept(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveExactly(4, connection: connection) { [weak self] header in
            guard let self, header.count == 4 else { connection.cancel(); return }
            let bytes = [UInt8](header)
            let length = (UInt32(bytes[0]) << 24) | (UInt32(bytes[1]) << 16) | (UInt32(bytes[2]) << 8) | UInt32(bytes[3])
            guard length > 0, length <= maximumFrameBytes else { connection.cancel(); return }
            self.receiveExactly(Int(length), connection: connection) { payload in
                DispatchQueue.main.async { self.handle(payload, connection: connection) }
            }
        }
    }

    private func receiveExactly(_ count: Int, connection: NWConnection, completion: @escaping (Data) -> Void) {
        var accumulated = Data()
        func next() {
            connection.receive(minimumIncompleteLength: 1, maximumLength: count - accumulated.count) { data, _, _, error in
                if let data { accumulated.append(data) }
                guard error == nil, accumulated.count < count else {
                    if accumulated.count == count { completion(accumulated) } else { connection.cancel() }
                    return
                }
                next()
            }
        }
        next()
    }

    private func handle(_ data: Data, connection: NWConnection) {
        do {
            let envelope = try JSONDecoder().decode(SecureEnvelope.self, from: data)
            guard envelope.serverID == pairing.state.serverID else { throw SyncError.unauthorized }
            switch envelope.kind {
            case "pair":
                guard let pending = pairing.state.pendingKey else { throw SyncError.unauthorized }
                let request = try SyncCrypto.open(PairRequest.self, envelope: envelope, key: pending)
                let session = try pairing.pair(deviceID: request.deviceID, deviceName: request.deviceName)
                pairedDeviceName = request.deviceName
                try send(PairResponse(sessionKey: session.base64EncodedString()), kind: "pair_response", deviceID: request.deviceID, key: pending, connection: connection)
                pairing.confirmAuthenticatedSession()
            case "sync":
                guard envelope.deviceID == pairing.state.deviceID, let key = pairing.state.sessionKey else { throw SyncError.unauthorized }
                let request = try SyncCrypto.open(SyncRequest.self, envelope: envelope, key: key)
                let ids = store.receive(request.items)
                try send(SyncResponse(acknowledgedIDs: ids), kind: "sync_response", deviceID: envelope.deviceID, key: key, connection: connection)
                NSLog("Sync accepted deviceID=%@ itemCount=%d acknowledgedCount=%d", envelope.deviceID ?? "", request.items.count, ids.count)
            case "unbind":
                guard envelope.deviceID == pairing.state.deviceID, let key = pairing.state.sessionKey else { throw SyncError.unauthorized }
                _ = try SyncCrypto.open(UnbindRequest.self, envelope: envelope, key: key)
                try send(SyncResponse(acknowledgedIDs: []), kind: "unbind_response", deviceID: envelope.deviceID, key: key, connection: connection)
                revoke()
            default: throw SyncError.unsupportedMessage
            }
        } catch {
            NSLog("Sync request rejected byteCount=%d error=%@", data.count, String(describing: error))
            connection.cancel()
        }
    }

    private func send<T: Encodable>(_ payload: T, kind: String, deviceID: String?, key: Data, connection: NWConnection) throws {
        let envelope = try SyncCrypto.seal(payload, kind: kind, serverID: pairing.state.serverID, deviceID: deviceID, key: key)
        let data = try JSONEncoder().encode(envelope)
        var length = UInt32(data.count).bigEndian
        var frame = Data(bytes: &length, count: 4); frame.append(data)
        connection.send(content: frame, completion: .contentProcessed { _ in connection.cancel() })
    }
}
