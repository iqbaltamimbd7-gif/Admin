package com.sayma.builders.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.R

@Composable
fun CyberSplashScreen() {
    val monitorShape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF65B8F3), Color(0xFF2586DA), Color(0xFF0750A4), Color(0xFF063B83))
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Cyber Builders Portal",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = .2.sp,
                textAlign = TextAlign.Center
            )

            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.fillMaxWidth().widthIn(max = 440.dp)
                        .shadow(24.dp, monitorShape)
                        .clip(monitorShape)
                        .background(Color(0xFF071626))
                        .border(1.dp, Color.White.copy(alpha = .45f), monitorShape)
                        .padding(7.dp)
                ) {
                    Image(
                        painterResource(R.drawable.cyber_dashboard_full),
                        contentDescription = "Cyber Builders desktop portal preview",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1891f / 905f)
                            .clip(RoundedCornerShape(15.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                Box(
                    Modifier.width(25.dp).height(25.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF9BC9E9), Color(0xFF245B91))),
                            RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                        )
                )
                Box(
                    Modifier.width(142.dp).height(12.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF1D6EB3), Color(0xFF0B3E79))))
                        .border(1.dp, Color.White.copy(alpha = .28f), CircleShape)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Powered by", color = Color.White.copy(alpha = .88f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cyber Builders SDN BHD",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
