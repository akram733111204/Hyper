package com.rukn.expiry.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Evergreen=Color(0xFF0B6252)
private val Mint=Color(0xFF147A61)
private val Gold=Color(0xFFF2BC3C)
private val Light=lightColorScheme(primary=Evergreen,onPrimary=Color.White,primaryContainer=Color(0xFFD5F0E4),
    onPrimaryContainer=Color(0xFF123D31),secondary=Mint,tertiary=Gold,
    background=Color(0xFFF6F8F5),surface=Color.White,surfaceVariant=Color(0xFFEAF0ED),
    onSurface=Color(0xFF17332D),onSurfaceVariant=Color(0xFF52635C),outline=Color(0xFFB9CCC3),
    error=Color(0xFFC8333C))
private val Dark=darkColorScheme(primary=Color(0xFF89DDBF),onPrimary=Color(0xFF07362D),primaryContainer=Evergreen,
    secondary=Color(0xFF85CDB8),tertiary=Gold,background=Color(0xFF0B1915),surface=Color(0xFF15251F),
    surfaceVariant=Color(0xFF21392F),onSurface=Color(0xFFE6F0E9),onSurfaceVariant=Color(0xFFB3C7B9),error=Color(0xFFFF8C8F))
@Composable fun RuknTheme(content:@Composable ()->Unit){
    MaterialTheme(colorScheme=if(isSystemInDarkTheme())Dark else Light,content=content)
}
