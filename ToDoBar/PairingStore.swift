import Foundation
import Security

final class PairingStore {
    struct State: Codable {
        var serverID: String
        var pendingKey: Data?
        var deviceID: String?
        var deviceName: String?
        var sessionKey: Data?
    }
    private let service = "com.chenxiaocai.ToDoBarSync.pairing"
    private let account = "state"
    private(set) var state: State

    init() {
        if let data = Self.read(service: service, account: account), let stored = try? JSONDecoder().decode(State.self, from: data) {
            state = stored
        } else {
            state = State(serverID: UUID().uuidString, pendingKey: SyncCrypto.randomKey(), deviceID: nil, deviceName: nil, sessionKey: nil)
            persist()
        }
    }

    var qrString: String? {
        guard state.deviceID == nil, let key = state.pendingKey else { return nil }
        let encoded = key.base64EncodedString()
        return "todobar-sync://pair?v=1&server=\(state.serverID)&key=\(encoded.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? encoded)"
    }

    func pair(deviceID: String, deviceName: String) throws -> Data {
        if let existing = state.deviceID, existing != deviceID { throw SyncError.unauthorized }
        if state.sessionKey == nil { state.sessionKey = SyncCrypto.randomKey() }
        state.deviceID = deviceID
        state.deviceName = deviceName
        persist()
        return state.sessionKey!
    }

    func confirmAuthenticatedSession() { state.pendingKey = nil; persist() }
    func revoke() {
        state.deviceID = nil; state.deviceName = nil; state.sessionKey = nil; state.pendingKey = SyncCrypto.randomKey(); persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(state) else { return }
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        NSLog("Pairing state persisted serverID=%@ paired=%@ keychainStatus=%d", state.serverID, state.deviceID == nil ? "false" : "true", status)
    }

    private static func read(service: String, account: String) -> Data? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var value: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &value) == errSecSuccess else { return nil }
        return value as? Data
    }
}
