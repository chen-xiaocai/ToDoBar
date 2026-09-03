import Foundation
import CryptoKit

let syncProtocolVersion = 1
let maximumFrameBytes = 1_048_576

struct IncomingTodo: Codable { let id: String; let text: String; let createdAt: Int64 }
struct PairRequest: Codable { let deviceID: String; let deviceName: String }
struct PairResponse: Codable { let sessionKey: String }
struct SyncRequest: Codable { let items: [IncomingTodo] }
struct SyncResponse: Codable { let acknowledgedIDs: [String] }
struct UnbindRequest: Codable { let requestedAt: Int64 }

struct SecureEnvelope: Codable {
    let version: Int
    let kind: String
    let serverID: String
    let deviceID: String?
    let sealedPayload: String

    func authenticatedData() -> Data {
        Data("\(version)|\(kind)|\(serverID)|\(deviceID ?? "")".utf8)
    }
}

enum SyncCrypto {
    static func randomKey() -> Data {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes)
    }

    static func seal<T: Encodable>(_ payload: T, kind: String, serverID: String, deviceID: String?, key: Data) throws -> SecureEnvelope {
        var envelope = SecureEnvelope(version: syncProtocolVersion, kind: kind, serverID: serverID, deviceID: deviceID, sealedPayload: "")
        let plaintext = try JSONEncoder().encode(payload)
        let box = try AES.GCM.seal(plaintext, using: SymmetricKey(data: key), authenticating: envelope.authenticatedData())
        guard let combined = box.combined else { throw SyncError.invalidCiphertext }
        envelope = SecureEnvelope(version: envelope.version, kind: kind, serverID: serverID, deviceID: deviceID, sealedPayload: combined.base64EncodedString())
        return envelope
    }

    static func open<T: Decodable>(_ type: T.Type, envelope: SecureEnvelope, key: Data) throws -> T {
        guard envelope.version == syncProtocolVersion,
              let combined = Data(base64Encoded: envelope.sealedPayload) else { throw SyncError.invalidEnvelope }
        let box = try AES.GCM.SealedBox(combined: combined)
        let plaintext = try AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: envelope.authenticatedData())
        return try JSONDecoder().decode(type, from: plaintext)
    }
}

enum SyncError: Error { case invalidEnvelope, invalidCiphertext, unauthorized, oversizedFrame, unsupportedMessage }
