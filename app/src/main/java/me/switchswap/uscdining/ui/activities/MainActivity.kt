package me.switchswap.uscdining.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.datetime.datePicker
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import me.switchswap.diningmenu.models.DiningHallType
import me.switchswap.uscdining.R
import me.switchswap.uscdining.data.MenuDao
import me.switchswap.uscdining.extensions.db
import me.switchswap.uscdining.ui.adapters.MenuPagerAdapter
import me.switchswap.uscdining.ui.interfaces.IFragmentInteractionListener
import me.switchswap.uscdining.ui.viewmodels.MainActivityViewModel
import me.switchswap.uscdining.util.DateUtil
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, IFragmentInteractionListener {
    private val viewModel: MainActivityViewModel by viewModels()

    private val viewPager by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<ViewPager>(R.id.viewpager)
    }

    private val tabLayout by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<TabLayout>(R.id.tablayout)
    }

    private val dateUtil by lazy {
        DateUtil(this)
    }

    private lateinit var menuDao: MenuDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        // Set navigation drawer listener
        nav_view.setNavigationItemSelectedListener(this)
        viewPager.offscreenPageLimit = 2

        // Set menuDao
        menuDao = applicationContext.db().menuDao()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Set system prefs date
        dateUtil.writeDate(DateUtil.convertDate(Calendar.getInstance()))

        // Set action bar date
        setActionBarDate()

        // Initialize views and setup view pager
        setupViewPager()

        // Set button listener for date button
        fab_date.setOnClickListener {
            // Create date picker on click
            MaterialDialog(this).show {
                val currentDate = Calendar.getInstance().apply {
                    time = Date(dateUtil.readDate())
                }

                // Set max date to 12 days past the current date in accordance with the website
                val maxDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 12)
                }

                datePicker(currentDate = currentDate, maxDate = maxDate) { _, date ->
                    // Use date (Calendar)
                    setActionBarDate(date)

                    // Update shared preference
                    val unixTimeStamp = DateUtil.convertDate(date)
                    dateUtil.writeDate(unixTimeStamp)
                }
            }
        }

        nav_settings.setOnClickListener {
            val settingsIntent = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(settingsIntent)
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu
        // This adds items to the action bar if it is present.
        // menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        changeViewPager(item.itemId)
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * Setup [ViewPager] such that it shows the correct tab as set by the user in settings
     */
    private fun setupViewPager() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Todo: Make values not hard-coded (for changeViewPager() too)
        when (sharedPreferences.getString(getString(R.string.pref_default_hall), "") ?: "") {
            "evk" -> changeViewPager(R.id.nav_evk)
            "parkside" -> changeViewPager(R.id.nav_parkside)
            "village" -> changeViewPager(R.id.nav_village)
            else -> changeViewPager(R.id.nav_evk)
        }
    }

    /**
     * @param selectedItem is the item selected from the navigation drawer
     * @see onOptionsItemSelected
     */
    private fun changeViewPager(@IdRes selectedItem : Int) {
        val fragmentPagerAdapter : FragmentStatePagerAdapter? = when(selectedItem) {
            R.id.nav_evk -> {
                title = "EVK"
                nav_view.setCheckedItem(R.id.nav_evk)
                MenuPagerAdapter(supportFragmentManager, DiningHallType.EVK)
            }
            R.id.nav_parkside -> {
                title = "Parkside"
                nav_view.setCheckedItem(R.id.nav_parkside)
                MenuPagerAdapter(supportFragmentManager, DiningHallType.PARKSIDE)
            }
            R.id.nav_village -> {
                title = "Village"
                nav_view.setCheckedItem(R.id.nav_village)
                MenuPagerAdapter(supportFragmentManager, DiningHallType.VILLAGE)
            }
            else -> null
        }
        viewModel.currentDiningHall = selectedItem
        fragmentPagerAdapter?.also { adapter ->
            viewPager.adapter = adapter
            tabLayout.setupWithViewPager(viewPager)
        }
    }

    private fun setActionBarDate(calendar: Calendar = Calendar.getInstance()) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val toolbarDate : TextView? = findViewById(R.id.toolbar_date)
        toolbarDate?.text = getString(R.string.date_string, month + 1, day, year % 100)
    }

    /**
     * Updates the navigation view depending on which dining halls are open
     */
    // Todo: May not be important but the calls to `findItem` can possibly be reduced
    override fun configureDiningHalls(date: Long) {
        // If no halls are open, grey all of them as usual and also disable the tabs
        if (!menuDao.dateHasMenu(date)) {
            disableNavDrawer()
        }
        else {
            nav_view.menu.findItem(R.id.nav_parkside)?.isEnabled =  menuDao.hallHasMenu(DiningHallType.PARKSIDE.id, date)
            nav_view.menu.findItem(R.id.nav_village)?.isEnabled = menuDao.hallHasMenu(DiningHallType.VILLAGE.id, date)
            nav_view.menu.findItem(R.id.nav_evk)?.isEnabled = menuDao.hallHasMenu(DiningHallType.EVK.id, date)

            // If current hall is closed, move to first open hall
            if (nav_view.menu.findItem(viewModel.currentDiningHall)?.isEnabled == false) {
                for (id in listOf(R.id.nav_parkside, R.id.nav_village, R.id.nav_evk)) {
                    if (nav_view.menu.findItem(id)?.isEnabled == true) {
                        changeViewPager(id)
                        return
                    }
                }
            }
        }
    }

    override fun configureBrunch(diningHallType: DiningHallType, date: Long) {
        val breakfastTab: TextView? = tabLayout.getTabAt(0)?.view?.getChildAt(1) as TextView?

        if (menuDao.hallHasBrunch(diningHallType, date)) {
            breakfastTab?.text = getString(R.string.tab_brunch)
        }
        else {
            breakfastTab?.text = getString(R.string.tab_breakfast)
        }
    }

    override fun getRefreshing(): Boolean {
        return viewModel.getRefreshing()
    }

    override fun setRefreshing(status: Boolean) {
        viewModel.setRefreshing(status)
    }

    override fun disableNavDrawer() {
        nav_view.menu.findItem(R.id.nav_evk)?.isEnabled = false
        nav_view.menu.findItem(R.id.nav_parkside)?.isEnabled = false
        nav_view.menu.findItem(R.id.nav_village)?.isEnabled = false
    }

    companion object {
        val TAG = MainActivity::class.java.simpleName
    }
}
