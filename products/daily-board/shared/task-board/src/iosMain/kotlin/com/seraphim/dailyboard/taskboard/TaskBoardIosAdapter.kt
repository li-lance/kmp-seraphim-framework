package com.seraphim.dailyboard.taskboard

class TaskBoardIosAdapter {
    private val board = TaskBoard()

    fun createTaskTitle(title: String): String = board.createTask(title).tasks.last().title
}
