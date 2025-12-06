//
//  MenuView.swift
//  ToDoBar
//
//  Created by Pavel Makhov on 2025-02-24.
//

import SwiftUI
import LaunchAtLogin
import Defaults

struct MenuView: View {
    @Binding var todos: [Todo]
    @Default(.showTaskCount) var showTaskCount
    
    var body: some View {
        Menu {
            Button(action: {
                todos = todos.filter{ !$0.isDone }
                (NSApplication.shared.delegate as? AppDelegate)?.updateStatusBarButton()
            }) {
                Label("Clear Done", systemImage: "eyeglasses")
            }
            Button(action: {
                todos.removeAll()
                (NSApplication.shared.delegate as? AppDelegate)?.updateStatusBarButton()
            }) {
                Label("Clear All", systemImage: "book")
            }
            Divider()
            LaunchAtLogin.Toggle()
            Toggle("Show Task Count", isOn: $showTaskCount)
                .onChange(of: showTaskCount) { _ in
                    (NSApplication.shared.delegate as? AppDelegate)?.updateStatusBarButton()
                }
            Divider()
            Button(action: { (NSApplication.shared.delegate as? AppDelegate)?.openAboutWindow(nil) } ) {
                Label("About ToDoBar", systemImage: "books.vertical")
            }
            Button(action: { (NSApplication.shared.delegate as? AppDelegate)?.quit() }) {
                Label("Quit", systemImage: "books.vertical")
            }
        } label: {
            Image(systemName: "chevron.down")
        }
        .labelsHidden()
        .scaledToFit()
        .menuStyle(BorderlessButtonMenuStyle())
        .menuIndicator(.hidden)
        .frame(width: 16, height: 16)
        .padding(.vertical, 8)
        .padding(.leading, 10)
        .padding(.trailing, 6)
        .background(Color.accentColor)
        .cornerRadius(8)
        .contentShape(Rectangle())
    }
}

//#Preview {
//    MenuView()
//}
