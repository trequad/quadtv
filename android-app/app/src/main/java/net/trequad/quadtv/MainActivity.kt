package net.trequad.quadtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import net.trequad.quadtv.home.HomeFragment

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, HomeFragment())
                .commitNow()
        }
    }
}
