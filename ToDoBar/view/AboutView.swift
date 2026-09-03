//
//  AboutView.swift
//  ToDoBar
//
//  Created by Pavel Makhov on 2023-07-07.
//

import SwiftUI

struct AboutView: View {
    let currentVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as! String
    @Environment(\.openURL) var openURL
    
    var body: some View {

        VStack {
            Image(nsImage: NSImage(named: "AppIcon")!)
            Text("ToDoBar Sync").font(.title)
            Text("ToDoBar 的局域网同步分支").font(.caption)
            Text("version " + currentVersion).font(.footnote)
            Divider()
            Button(action: {
                openURL(URL(string:"https://github.com/chen-xiaocai/ToDoBar")!)
            }) {
                HStack {
                    Image(systemName: "house.fill")
                    Text("Home Page")
                }
                .frame(maxWidth: 160)
                
            }
            .buttonStyle(.borderless)
            .padding(4)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.accentColor, lineWidth: 1)
            )
            
            Button(action: {
                openURL(URL(string:"https://github.com/chen-xiaocai/ToDoBar/issues")!)
            }) {
                HStack {
                    Image(systemName: "star.fill")
                    Text("Request a Feature")
                }
                .frame(maxWidth: 160)
            }
            .buttonStyle(.borderless)
            .padding(4)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.accentColor, lineWidth: 1)
            )
            
            Button(action: {
                openURL(URL(string:"https://github.com/chen-xiaocai/ToDoBar/issues")!)
            }) {
                HStack {
                    Image(systemName: "ladybug.fill")
                    Text("Report a Bug")
                }
                .frame(maxWidth: 160)
            }
            .buttonStyle(.borderless)
            .padding(4)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.accentColor, lineWidth: 1)
            )
            
            Divider()
            AppPromotionView()
        }.padding()
        
        
    }
}

struct App: Identifiable {
    let id = UUID()
    let name: String
    let description: String
    let iconName: String
    let appStoreURL: String
}

#Preview {
    AboutView()
        .frame(height: 500)
}
