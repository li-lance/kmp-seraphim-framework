import SwiftUI
import TaskBoardShared

struct ContentView: View {
    private let title = TaskBoardIosAdapter().createTaskTitle(title: "First task")

    var body: some View {
        Text(title)
            .padding()
    }
}
