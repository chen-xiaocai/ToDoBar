//
//  ToDo.swift
//  ToDoBar
//
//  Created by Pavel Makhov on 2022-08-24.
//

import Foundation

struct Todo: Codable, Identifiable, Equatable {
    let id: UUID
    var text: String
    var isDone: Bool = false
    let sourceID: String?

    init(id: UUID = UUID(), text: String, isDone: Bool = false, sourceID: String? = nil) {
        self.id = id
        self.text = text
        self.isDone = isDone
        self.sourceID = sourceID
    }
}
