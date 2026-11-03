package com.rukn.expiry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rukn.expiry.ui.RuknApp
import com.rukn.expiry.ui.RuknTheme

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent{
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
                RuknTheme { val vm:AppViewModel=viewModel();RuknApp(vm) }
            }
        }
    }
}
