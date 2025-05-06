package com.example.paymentgateway

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.paymentgateway.Utils.KEY
import com.example.paymentgateway.Utils.PUBLISHABLE_KEY
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var paymentSheet: PaymentSheet

    private var customerId by mutableStateOf<String?>(null)
    private var ephemeralKey by mutableStateOf<String?>(null)
    private var clientSecret by mutableStateOf<String?>(null)
    private var amount by mutableStateOf("")

    private var showSuccessAnimation by mutableStateOf(false)

    private val apiInterface = ApiUtilities.getApiInterface()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PaymentConfiguration.init(this, PUBLISHABLE_KEY)
        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)

        lifecycleScope.launch { getCustomerId() }

        setContent {
            PaymentScreen()
        }
    }

    @Composable
    fun PaymentScreen() {
        var amount by remember { mutableStateOf("") }
        var showSuccessDialog by remember { mutableStateOf(false) }

        val amountValue = amount.toIntOrNull() ?: 0

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Enter amount in cents") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Please enter the Amount more than 50 cents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        // Initiate your payment flow
                        // Use amount value here
                        if (amount.isNotEmpty()) {
                            paymentFlow(amount)
                        } else {
                            showError("Enter a valid amount")
                        }
                    },
                    enabled = amountValue >= 50 &&
                            customerId != null &&
                            ephemeralKey != null
                    ) {
                    Text("Pay Now")
                }
            }

            if (showSuccessDialog) {
                SuccessDialog(
                    message = "Payment Successful",
                    onDismiss = {
                        showSuccessDialog = false
                        amount = "" // Clear amount
                    }
                )
            }
        }
    }

    @Composable
    fun SuccessDialog(message: String, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LottieAnimation(
                        composition = rememberLottieComposition(LottieCompositionSpec.Asset("success.json")).value,
                        iterations = 1,
                        modifier = Modifier.size(150.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(message, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }

    private fun paymentFlow(amount: String) {
        if ( customerId == null || ephemeralKey == null) {
            Toast.makeText(this, "Payment not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        getPaymentIntent(customerId!!, ephemeralKey!!, amount)
    }

    private fun getCustomerId() {
        lifecycleScope.launch(Dispatchers.IO) {
            val res = apiInterface.getCustomer()
            withContext(Dispatchers.Main) {
                if (res.isSuccessful && res.body() != null) {
                    customerId = res.body()!!.id
                    getEphemeralKey(customerId!!)
                    Log.d("Stripe", "Customer created: ${res.body()}")
                } else {
                    showError("Failed to fetch customer ID ")
                    Log.e("Stripe", "Error: ${res.errorBody()?.string()}")
                }
            }
        }
    }

    private fun getEphemeralKey(customerId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val res = apiInterface.getEphemeralKey(customerId)
            withContext(Dispatchers.Main) {
                if (res.isSuccessful && res.body() != null) {
                    ephemeralKey = res.body()!!.secret
                    Log.d("Stripe", "Get Epheral key: ${res.body()}")
                } else {
                    Log.e("Stripe", "Error: ${res.errorBody()?.string()}")
                    Log.d("Stripe API", "Request URL: https://api.stripe.com/v1/ephemeral_keys")
                    Log.d("Stripe API", "Customer ID: $customerId")
                    Log.d("Stripe API", "Headers: Authorization: Bearer $KEY")
                    showError("Failed to fetch ephemeral key")
                }
            }
        }
    }

    private fun getPaymentIntent(customerId: String, ephemeralKey: String, amount: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val res = apiInterface.getPaymentIntent(customerId, amount)
            withContext(Dispatchers.Main) {
                if (res.isSuccessful && res.body() != null) {
                    clientSecret = res.body()!!.client_secret
                    Log.d("Stripe", "Get client secret ID: ${res.body()}")
                    Toast.makeText(this@MainActivity, "Payment ready", Toast.LENGTH_SHORT).show()

                    paymentSheet.presentWithPaymentIntent(
                        clientSecret!!,
                        PaymentSheet.Configuration(
                            merchantDisplayName = "Papaya Coder",
                            customer = PaymentSheet.CustomerConfiguration(
                                id = customerId,
                                ephemeralKeySecret = ephemeralKey
                            )
                        )
                    )
                } else {
                    Log.e("Stripe", "Error: ${res.errorBody()?.string()}")
                    showError("Failed to fetch payment intent")
                }
            }
        }
    }

    private fun onPaymentSheetResult(result: PaymentSheetResult) {
        when (result) {
            is PaymentSheetResult.Completed -> {
                Toast.makeText(this, "Payment Successful", Toast.LENGTH_SHORT).show()
                showSuccessAnimation = true
                amount = ""//amount cleared on success

                lifecycleScope.launch {
                    delay(3000)
                    showSuccessAnimation = false
                }
            }
            is PaymentSheetResult.Canceled -> {
                Log.e("STRIPE", "PaymentSheet was canceled")
                Toast.makeText(this, "Payment Canceled", Toast.LENGTH_SHORT).show()
                amount = ""
            }
            is PaymentSheetResult.Failed -> {
                Log.e("STRIPE", "Payment failed: ${result.error.localizedMessage}")
                Toast.makeText(this, "Payment Failed: ${result.error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
