//
//  AppDelegate.swift
//  ToDoBar
//
//  Created by Pavel Makhov on 2022-08-24.
//

import Cocoa
import SwiftUI
import HotKey
import Combine

class AppDelegate: NSObject, NSApplicationDelegate {
    
    var popover: NSPopover!
    var statusBarItem: NSStatusItem?
    let hotKey = HotKey(key: .x, modifiers: [.control, .shift])  // Global hotke
    var aboutWindow: NSWindow!
    private var syncWindowController: NSWindowController?
    let store = TodoStore()
    lazy var syncServer = SyncServer(store: store)
    private var cancellables = Set<AnyCancellable>()
    
    func applicationDidFinishLaunching(_ aNotification: Notification) {
        NSApp.setActivationPolicy(.accessory)

        let contentView = ContentView().environmentObject(store)
        
        let popover = NSPopover()
        popover.contentSize = NSSize(width: 400, height: 400)
        popover.behavior = .transient
        popover.contentViewController = NSHostingController(rootView: contentView)
        self.popover = popover
        
        let statusBarItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        self.statusBarItem = statusBarItem
        if let statusButton = statusBarItem.button {
            statusButton.image = NSImage(systemSymbolName: "checklist", accessibilityDescription: "ToDoBar Sync")
            statusButton.imagePosition = .imageLeft
            statusButton.target = self
            statusButton.action = #selector(togglePopover(_:))
        } else {
            NSLog("Status item button unavailable")
        }
        
        hotKey.keyUpHandler = { self.togglePopover(nil) }
        store.$todos.sink { [weak self] _ in self?.updateStatusBarButton() }.store(in: &cancellables)
        syncServer.start()
        
        updateStatusBarButton()
        
        if syncServer.pairedDeviceName == nil {
            DispatchQueue.main.async { [weak self] in
                self?.openSyncWindow(nil)
            }
        }
    }

    func applicationWillTerminate(_ notification: Notification) {
        syncServer.stop()
    }
    
    @objc func togglePopover(_ sender: AnyObject?) {
        if let button = self.statusBarItem?.button {
            if self.popover.isShown {
                self.popover.performClose(sender)
            } else {
                self.popover.show(relativeTo: button.bounds, of: button, preferredEdge: NSRectEdge.minY)
            }
        }
    }
    
    @objc
    func openAboutWindow(_: NSStatusBarButton?) {
        NSLog("Open about window")
        let contentView = AboutView()
        if aboutWindow != nil {
            aboutWindow.close()
        }
        aboutWindow = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 240, height: 500),
            styleMask: [.closable, .titled],
            backing: .buffered,
            defer: false
        )
        
        aboutWindow.title = "About"
        aboutWindow.contentView = NSHostingView(rootView: contentView)
        aboutWindow.makeKeyAndOrderFront(nil)
        aboutWindow.styleMask.remove(.resizable)
        
        // allow the preference window can be focused automatically when opened
        NSApplication.shared.activate(ignoringOtherApps: true)
        
        let controller = NSWindowController(window: aboutWindow)
        controller.showWindow(self)
        
        aboutWindow.center()
        aboutWindow.orderFrontRegardless()
    }

    @objc func openSyncWindow(_: Any?) {
        if let window = syncWindowController?.window {
            window.makeKeyAndOrderFront(nil)
            NSApplication.shared.activate(ignoringOtherApps: true)
            return
        }

        let syncWindow = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 430, height: 560),
            styleMask: [.closable, .titled], backing: .buffered, defer: false
        )
        syncWindow.isReleasedWhenClosed = false
        syncWindow.title = "ToDoBar Sync"
        syncWindow.contentView = NSHostingView(rootView: SyncSettingsView(server: syncServer))
        let controller = NSWindowController(window: syncWindow)
        syncWindowController = controller
        syncWindow.center()
        controller.showWindow(self)
        NSApplication.shared.activate(ignoringOtherApps: true)
    }
    
    @objc
    func quit() {
        NSLog("User click Quit")
        NSApplication.shared.terminate(self)
    }
}

extension AppDelegate {
    @objc
    func updateStatusBarButton() {
        if UserDefaults.standard.bool(forKey: "showTaskCount") {
            let unfinishedCount = store.todos.filter { !$0.isDone }.count
            self.statusBarItem?.button?.title = String(unfinishedCount)
        } else {
            statusBarItem?.button?.title = ""
        }
    }
}
