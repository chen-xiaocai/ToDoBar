import Foundation
import Combine

final class TodoStore: ObservableObject {
    @Published var todos: [Todo] = [] { didSet { save() } }
    private var receivedIDs = Set<String>()
    private var isLoading = true

    private struct State: Codable {
        var todos: [Todo]
        var receivedIDs: Set<String>
    }
    private struct LegacyTodo: Codable { let text: String; let isDone: Bool }

    private var stateURL: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("ToDoBarSync", isDirectory: true).appendingPathComponent("state.json")
    }

    init() {
        load()
        isLoading = false
    }

    func receive(_ items: [IncomingTodo]) -> [String] {
        var accepted: [String] = []
        for item in items where !receivedIDs.contains(item.id) {
            let trimmed = item.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty, trimmed.utf8.count <= 16_384 else { continue }
            todos.append(Todo(text: trimmed, sourceID: item.id))
            receivedIDs.insert(item.id)
            accepted.append(item.id)
        }
        save()
        return accepted + items.map(\.id).filter(receivedIDs.contains).filter { !accepted.contains($0) }
    }

    private func load() {
        do {
            let data = try Data(contentsOf: stateURL)
            let state = try JSONDecoder().decode(State.self, from: data)
            todos = state.todos
            receivedIDs = state.receivedIDs
            return
        } catch {
            NSLog("State load result path=%@ error=%@", stateURL.path, String(describing: error))
        }
        importOriginalData()
        save()
    }

    private func importOriginalData() {
        let path = NSString(string: "~/Library/Containers/com.pavelmakhov.ToDoBar/Data/Library/Preferences/com.pavelmakhov.ToDoBar.plist").expandingTildeInPath
        guard let plist = NSDictionary(contentsOfFile: path), let rows = plist["todos"] as? [String] else {
            NSLog("Original data import path=%@ importedCount=0", path)
            return
        }
        let decoder = JSONDecoder()
        todos = rows.compactMap { value in
            guard let data = value.data(using: .utf8), let old = try? decoder.decode(LegacyTodo.self, from: data) else { return nil }
            return Todo(text: old.text, isDone: old.isDone)
        }
        NSLog("Original data import path=%@ importedCount=%d", path, todos.count)
    }

    private func save() {
        guard !isLoading else { return }
        do {
            try FileManager.default.createDirectory(at: stateURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(State(todos: todos, receivedIDs: receivedIDs))
            try data.write(to: stateURL, options: [.atomic, .completeFileProtection])
            NSLog("State persisted todoCount=%d receivedIDCount=%d byteCount=%d", todos.count, receivedIDs.count, data.count)
        } catch {
            NSLog("State persist failed path=%@ error=%@", stateURL.path, String(describing: error))
        }
    }
}
