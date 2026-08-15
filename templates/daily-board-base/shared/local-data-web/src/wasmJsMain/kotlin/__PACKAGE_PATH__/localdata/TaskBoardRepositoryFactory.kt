package __PACKAGE_NAME__.localdata

import __PACKAGE_NAME__.taskboard.TaskBoardRepository

fun taskBoardRepository(): TaskBoardRepository = WebTaskBoardRepository(globalIndexedDb())
