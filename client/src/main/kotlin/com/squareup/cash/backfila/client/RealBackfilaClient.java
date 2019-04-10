package com.squareup.cash.backfila.client;

import com.squareup.moshi.Moshi;
import com.squareup.protos.backfila.service.ConfigureServiceRequest;
import com.squareup.protos.backfila.service.ConfigureServiceResponse;
import com.squareup.protos.backfila.service.ServiceType;
import com.squareup.wire.WireJsonAdapterFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;

import static com.google.common.base.Preconditions.checkState;

public class RealBackfilaClient implements BackfilaClient {
  private final BackfilaApi backfilaApi;

  public RealBackfilaClient(String host, OkHttpClient okHttpClient) {
    Moshi moshi = new Moshi.Builder()
        .add(new WireJsonAdapterFactory())
        .build();
    Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(host)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(okHttpClient)
        .build();
    this.backfilaApi = retrofit.create(BackfilaApi.class);
  }

  @Override public void configureService() {
    ConfigureServiceRequest request = new ConfigureServiceRequest.Builder()
        .service_type(ServiceType.CLOUD)
        .backfills(Collections.emptyList())
        .build();

      try {
        Response<ConfigureServiceResponse> response =
            backfilaApi.configureService(request).execute();
        checkState(response.isSuccessful());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
  }
}
