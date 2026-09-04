//
//  ContentView.swift
//  ToDoBar
//
//  Created by Pavel Makhov on 2022-08-24.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var store: TodoStore
    
    @State private var text: String = ""
    @State private var editedItem: String = ""
    
    @State private var newTodo: String = ""
    
    @State private var editedItemIdx: Int = -1
    @State private var hoverItemIdx: Int = -1
    @State private var hoverIdx: Int = -1
    
    @FocusState private var isTextFieldFocused: Bool
    @FocusState private var isTodoItemFocused: Bool
    
    var body: some View {
        VStack {
            TodoList(
                todos: $store.todos,
                editedItemIdx: $editedItemIdx,
                hoverIdx: $hoverIdx,
                hoverItemIdx: $hoverItemIdx,
                isTodoItemFocused: $isTodoItemFocused,
                editedItem: $editedItem
            )
            .scrollContentBackground(.hidden)
            
            HStack {
                NewTodoField(
                    newTodo: $newTodo,
                    isTextFieldFocused: $isTextFieldFocused,
                    onAddTodo: {
                        let value = newTodo.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !value.isEmpty else { return }
                        self.store.todos.append(Todo(text: value))
                        newTodo = ""
                        (NSApplication.shared.delegate as? AppDelegate)?.updateStatusBarButton()
                    }
                )
                
                Spacer()

                Button(action: {
                    (NSApplication.shared.delegate as? AppDelegate)?.openSyncWindow(nil)
                }) {
                    Label("手机同步", systemImage: "iphone.and.arrow.forward")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .help("打开手机同步与配对二维码")
                
                MenuView(todos: $store.todos)
            }
            .padding(8)
        }
    }
}
