package zip.zaop.paylink

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import net.openid.appauth.AuthorizationService
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.repository.LidlRepository
import java.util.concurrent.ExecutorService


class AccountsViewModel(application: Application) : AndroidViewModel(application) {
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mExecutor: ExecutorService? = null
    private val TAG = "accounts"

    private val lidlRepository = LidlRepository(getDatabase(application))

    // todo find out validity duration of wbw tokens
    // wbw room database i guess?
    // or use same technique as authstatemanager
    // OR make those ALL use a room db with acc info . . . would be nice for adding jumby and appho
    // cuz rn it does not support that
}