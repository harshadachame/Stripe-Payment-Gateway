package com.example.paymentgateway.api


import com.example.paymentgateway.Utils.KEY
import com.example.paymentgateway.model.CustomerModel
import com.example.paymentgateway.model.EpheralModel
import com.example.paymentgateway.model.PaymentIntentModel
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiInterface {

    @POST("v1/customers")
    suspend fun getCustomer(
    ): Response<CustomerModel>

    @POST("v1/ephemeral_keys")
    suspend fun getEphemeralKey(
        @Query("customer") customer: String
    ): Response<EpheralModel>

    @FormUrlEncoded
    @POST("v1/payment_intents")
    suspend fun getPaymentIntent(
        @Field("customer") customer: String,
        @Field("amount") amount: String = "100",
        @Field("currency") currency: String = "eur",
        @Field("automatic_payment_methods[enabled]") automaticPayment: Boolean = true
    ): Response<PaymentIntentModel>
}
