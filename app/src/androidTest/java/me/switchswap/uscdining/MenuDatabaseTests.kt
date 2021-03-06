package me.switchswap.uscdining

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import junit.framework.Assert.*
import kotlinx.coroutines.runBlocking
import me.switchswap.diningmenu.models.DiningHallType
import me.switchswap.diningmenu.models.ItemType
import me.switchswap.uscdining.data.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class MenuDatabaseTests {
    private lateinit var db: AppDatabase
    private lateinit var menuDao: MenuDao

    private val diningHall = DiningHall(1, "TestHall")
    private val menuItemA = MenuItem(0, "Soup", ItemType.BREAKFAST.name, "Temp", 1L, 1)
    private val menuItemB = MenuItem(0, "Bread", ItemType.BREAKFAST.name, "Temp", 2L, 1)
    private val menuItemC = MenuItem(0, "Cheese", ItemType.BREAKFAST.name, "Temp", 2L, 1)


    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()
        menuDao = db.menuDao()

        runBlocking {
            menuDao.insertDiningHalls(listOf(diningHall))
        }
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDbCreationTrue() {
        val diningHalls: List<DiningHall> = menuDao.getDiningHalls()
        var found = false
        for (diningHall: DiningHall in diningHalls) {
            if (diningHall.id == 1) {
                found = true
                break
            }
        }
        assertTrue(found)
    }

    @Test
    @Throws(Exception::class)
    fun testDbCreationFalse() {
        val diningHalls: List<DiningHall> = menuDao.getDiningHalls()
        var found = false
        for (diningHall: DiningHall in diningHalls) {
            if (diningHall.id == 5) {
                found = true
                break
            }
        }
        assertFalse(found)
    }

    @Test
    @Throws(Exception::class)
    fun testMenuItemInsert() {
        runBlocking {
            menuDao.insertMenuItem(menuItemA)
        }

        // Get all MenuItems from db along with their corresponding allergens
        val menuItemAndAllergens: List<MenuItemAndAllergens> = menuDao.getMenuItems(1, ItemType.BREAKFAST, 1L)
        assertEquals(1, menuItemAndAllergens.size)
        assertEquals("Soup", menuItemAndAllergens[0].menuItem.name)
        assertEquals(ItemType.BREAKFAST.name, menuItemAndAllergens[0].menuItem.type)
        assertEquals("Temp", menuItemAndAllergens[0].menuItem.category)
        assertEquals(1L, menuItemAndAllergens[0].menuItem.date)
        assertEquals(1, menuItemAndAllergens[0].menuItem.hallId)

        // There should be no allergens returned since there were none inserted
        assertEquals(0, menuItemAndAllergens[0].allergens.size)
    }

    @Test
    @Throws(Exception::class)
    fun testAllergenInsert() {
        runBlocking {
            val menuItemId: Int = menuDao.insertMenuItem(menuItemA).toInt()

            menuDao.insertAllergens(listOf(Allergen(0,"Chicken",menuItemId),
                    Allergen(0,"Eggs", menuItemId),
                    Allergen(0,"Dairy", menuItemId)))
        }

        // Get all MenuItems from db along with their corresponding allergens
        val menuItemAndAllergens: List<MenuItemAndAllergens> =
                menuDao.getMenuItems(1, ItemType.BREAKFAST, 1L)
        assertEquals(1, menuItemAndAllergens.size)
        assertEquals(3, menuItemAndAllergens[0].allergens.size)
    }

    @Test
    @Throws(Exception::class)
    fun testMenuItemDropAll() {
        runBlocking {
            // Insert a MenuItem
            val menuItemId: Int = menuDao.insertMenuItem(menuItemA).toInt()

            // Insert an Allergen
            menuDao.insertAllergens(listOf(Allergen(0,"Chicken",menuItemId)))
        }

        // Verify that these were added
        var menuItemAndAllergens: List<MenuItemAndAllergens> =
                menuDao.getMenuItems(1, ItemType.BREAKFAST, 1L)
        assertEquals(1, menuItemAndAllergens.size)
        assertEquals(1, menuItemAndAllergens[0].allergens.size)

        runBlocking {
            menuDao.dropAllMenuItems()
        }

        // Verify that the item was dropped
        menuItemAndAllergens = menuDao.getMenuItems(1, ItemType.BREAKFAST, 1L)
        assertEquals(0, menuItemAndAllergens.size)
    }

    @Test
    @Throws(Exception::class)
    fun testMenuItemDrop() {
        runBlocking {
            listOf(menuItemA, menuItemB, menuItemC).forEach { menuItem ->
                // Insert a MenuItem
                val menuItemId: Int = menuDao.insertMenuItem(menuItem).toInt()

                // Insert an Allergen
                menuDao.insertAllergens(listOf(Allergen(0,"Chicken",menuItemId)))
            }
        }

        // Verify that these were added
        var menuItemAndAllergens: List<MenuItemAndAllergens> =
                menuDao.getMenuItems(1, ItemType.BREAKFAST, 2L)
        assertEquals(2, menuItemAndAllergens.size)
        assertEquals(1, menuItemAndAllergens[0].allergens.size)
        assertEquals(1, menuItemAndAllergens[1].allergens.size)

        runBlocking {
            menuDao.dropMenuItems(2L)
        }

        // Verify that the item was dropped
        menuItemAndAllergens = menuDao.getMenuItems(1, ItemType.BREAKFAST, 2L)
        assertEquals(0, menuItemAndAllergens.size)
    }

    @Test
    @Throws(Exception::class)
    fun testDiningHallTypeConverter() {
        runBlocking {
            menuDao.insertDiningHalls(listOf(DiningHall(DiningHallType.EVK.id, DiningHallType.EVK.name)))
            menuDao.insertMenuItem(MenuItem(0, "Soup", ItemType.BREAKFAST.name, "Temp", 1L, DiningHallType.EVK.id))
        }

        // Verify that the item was dropped
        val menuItemAndAllergens: List<MenuItemAndAllergens> =
                menuDao.getMenuItems(DiningHallType.EVK, ItemType.BREAKFAST, 1L)
        assertEquals(1, menuItemAndAllergens.size)
    }

    @Test
    @Throws(Exception::class)
    fun testItemTypeConverter() {
        runBlocking {
            menuDao.insertDiningHalls(listOf(DiningHall(DiningHallType.EVK.id, DiningHallType.EVK.name)))
            menuDao.insertMenuItem(MenuItem(0, "Soup", ItemType.BREAKFAST.name, "Temp", 1L, DiningHallType.EVK.id))
        }

        // Verify that the item was dropped
        val menuItemAndAllergens: List<MenuItemAndAllergens> =
                menuDao.getMenuItems(DiningHallType.EVK, ItemType.BREAKFAST, 1L)
        assertEquals(1, menuItemAndAllergens.size)
    }

    companion object {
        @Suppress("unused")
        val TAG = MenuDatabaseTests::class.java.simpleName
    }
}
