package __PACKAGE_NAME__.localdata

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// commonTest 的 RepositoryContractTest 在 androidHostTest 中直接运行拿不到
// Robolectric 环境（RuntimeEnvironment.getApplication() 为 null），因此经
// @RunWith(RobolectricTestRunner) 的子类运行同一套合约；基类在
// testAndroidHostTest 的 filter 中排除，避免 JUnit4 双跑。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidRepositoryContractTest : RepositoryContractTest()
