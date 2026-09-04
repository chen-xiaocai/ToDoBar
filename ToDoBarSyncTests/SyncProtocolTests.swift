import XCTest
@testable import ToDoBarSync

final class SyncProtocolTests: XCTestCase {
    func testPairingTicketEscapesBase64ReservedCharacters() throws {
        let raw = "+/=="
        let encoded = PairingStore.encodeQueryValue(raw)

        XCTAssertEqual(encoded, "%2B%2F%3D%3D")
        let components = try XCTUnwrap(URLComponents(string: "todobar-sync://pair?key=\(encoded)"))
        XCTAssertEqual(components.queryItems?.first(where: { $0.name == "key" })?.value, raw)
    }

    func testEncryptedRoundTripAndAuthentication() throws {
        let key = SyncCrypto.randomKey()
        let input = SyncRequest(items: [IncomingTodo(id: "41e4e5e5-7c80-476d-a7e1-aca0a728edb5", text: "sample", createdAt: 1)])
        let envelope = try SyncCrypto.seal(input, kind: "sync", serverID: "server", deviceID: "device", key: key)
        let output = try SyncCrypto.open(SyncRequest.self, envelope: envelope, key: key)
        XCTAssertEqual(output.items.first?.id, input.items.first?.id)
        XCTAssertThrowsError(try SyncCrypto.open(SyncRequest.self, envelope: envelope, key: SyncCrypto.randomKey()))
    }
}
