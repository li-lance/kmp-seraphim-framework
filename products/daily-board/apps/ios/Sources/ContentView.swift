import SwiftUI
import TaskBoardShared
import LocalDataSql

struct ContentView: View {
    private let adapter: TaskBoardIosAdapter
    @State private var snapshot: BoardSnapshot? = nil
    @State private var openError: String? = nil

    init() {
        self.adapter = TaskBoardIosAdapter(
            store: TaskBoardStore(
                repository: TaskBoardRepositoryFactories_iosKt.taskBoardRepository(name: "daily-board.db") as! TaskBoardRepository
            )
        )
    }

    var body: some View {
        VStack {
            if let error = openError {
                Text("Error: \(error)")
            } else if let snapshot {
                Text(snapshot.columns.map(\.name).joined(separator: " / "))
            } else {
                Text("Loading...")
            }
        }
        .padding()
        .onAppear {
            adapter.open { error in
                openError = error
                guard error == nil else { return }
                adapter.startObserving { boardSnapshot in
                    snapshot = boardSnapshot
                }
            }
        }
        .onDisappear {
            adapter.stopObserving()
        }
    }
}
