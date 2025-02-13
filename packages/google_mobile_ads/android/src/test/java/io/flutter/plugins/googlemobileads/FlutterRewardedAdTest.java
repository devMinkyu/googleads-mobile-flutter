// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.flutter.plugins.googlemobileads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import androidx.annotation.Nullable;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.rewarded.OnAdMetadataChangedListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.googlemobileads.FlutterAd.FlutterLoadAdError;
import io.flutter.plugins.googlemobileads.FlutterRewardedAd.FlutterRewardItem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FlutterRewardedAd}. */
@RunWith(RobolectricTestRunner.class)
public class FlutterRewardedAdTest {

  private FlutterAdLoader mockFlutterAdLoader;
  private AdInstanceManager mockManager;
  private AdManagerAdRequest mockAdManagerAdRequest;
  private AdRequest mockAdRequest;

  // The system under test.
  private FlutterRewardedAd flutterRewardedAd;

  @Before
  public void setup() {
    mockManager = spy(new AdInstanceManager(mock(MethodChannel.class)));
    doReturn(mock(Activity.class)).when(mockManager).getActivity();
    mockFlutterAdLoader = mock(FlutterAdLoader.class);
  }

  private void setupAdmobMocks(@Nullable FlutterServerSideVerificationOptions options) {
    FlutterAdRequest mockFlutterAdRequest = mock(FlutterAdRequest.class);
    mockAdRequest = mock(AdRequest.class);
    when(mockFlutterAdRequest.asAdRequest()).thenReturn(mockAdRequest);
    flutterRewardedAd =
        new FlutterRewardedAd(
            1, mockManager, "testId", mockFlutterAdRequest, options, mockFlutterAdLoader);
  }

  private void setupAdManagerMocks(@Nullable FlutterServerSideVerificationOptions options) {
    FlutterAdManagerAdRequest mockAdManagerFlutterRequest = mock(FlutterAdManagerAdRequest.class);
    mockAdManagerAdRequest = mock(AdManagerAdRequest.class);
    when(mockAdManagerFlutterRequest.asAdManagerAdRequest()).thenReturn(mockAdManagerAdRequest);
    flutterRewardedAd =
        new FlutterRewardedAd(
            1, mockManager, "testId", mockAdManagerFlutterRequest, options, mockFlutterAdLoader);
  }

  @Test
  public void loadAdManagerRewardedAd_failedToLoad() {
    setupAdManagerMocks(null);
    final LoadAdError loadAdError = mock(LoadAdError.class);
    doReturn(1).when(loadAdError).getCode();
    doReturn("2").when(loadAdError).getDomain();
    doReturn("3").when(loadAdError).getMessage();
    doReturn(null).when(loadAdError).getResponseInfo();
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                RewardedAdLoadCallback adLoadCallback = invocation.getArgument(2);
                // Pass back null for ad
                adLoadCallback.onAdFailedToLoad(loadAdError);
                return null;
              }
            })
        .when(mockFlutterAdLoader)
        .loadAdManagerRewarded(
            anyString(), any(AdManagerAdRequest.class), any(RewardedAdLoadCallback.class));

    flutterRewardedAd.load();

    verify(mockFlutterAdLoader)
        .loadAdManagerRewarded(
            eq("testId"), eq(mockAdManagerAdRequest), any(RewardedAdLoadCallback.class));

    FlutterLoadAdError expectedError = new FlutterLoadAdError(loadAdError);
    verify(mockManager).onAdFailedToLoad(eq(1), eq(expectedError));
  }

  @Test
  public void loadAdManagerRewardedAd_showSuccessWithReward() {
    final FlutterServerSideVerificationOptions options =
        new FlutterServerSideVerificationOptions("userId", "customData");
    setupAdManagerMocks(options);

    final RewardedAd mockAd = mock(RewardedAd.class);
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                RewardedAdLoadCallback adLoadCallback = invocation.getArgument(2);
                // Pass back null for ad
                adLoadCallback.onAdLoaded(mockAd);
                return null;
              }
            })
        .when(mockFlutterAdLoader)
        .loadAdManagerRewarded(
            anyString(), any(AdManagerAdRequest.class), any(RewardedAdLoadCallback.class));
    final ResponseInfo responseInfo = mock(ResponseInfo.class);
    doReturn(responseInfo).when(mockAd).getResponseInfo();

    final AdValue adValue = mock(AdValue.class);
    doReturn(1).when(adValue).getPrecisionType();
    doReturn("Dollars").when(adValue).getCurrencyCode();
    doReturn(1000L).when(adValue).getValueMicros();
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) {
                FlutterPaidEventListener listener = invocation.getArgument(0);
                listener.onPaidEvent(adValue);
                return null;
              }
            })
        .when(mockAd)
        .setOnPaidEventListener(any(FlutterPaidEventListener.class));

    flutterRewardedAd.load();

    verify(mockFlutterAdLoader)
        .loadAdManagerRewarded(
            eq("testId"), eq(mockAdManagerAdRequest), any(RewardedAdLoadCallback.class));

    verify(mockManager).onAdLoaded(eq(1), eq(responseInfo));
    verify(mockAd).setOnPaidEventListener(any(FlutterPaidEventListener.class));
    final ArgumentCaptor<FlutterAdValue> adValueCaptor = forClass(FlutterAdValue.class);
    verify(mockManager).onPaidEvent(eq(flutterRewardedAd), adValueCaptor.capture());
    assertEquals(adValueCaptor.getValue().currencyCode, "Dollars");
    assertEquals(adValueCaptor.getValue().precisionType, 1);
    assertEquals(adValueCaptor.getValue().valueMicros, 1000L);

    // Setup mocks for show().
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                FullScreenContentCallback callback = invocation.getArgument(0);
                callback.onAdShowedFullScreenContent();
                callback.onAdImpression();
                callback.onAdDismissedFullScreenContent();
                return null;
              }
            })
        .when(mockAd)
        .setFullScreenContentCallback(any(FullScreenContentCallback.class));

    final RewardItem mockRewardItem = mock(RewardItem.class);
    doReturn(5).when(mockRewardItem).getAmount();
    doReturn("$$").when(mockRewardItem).getType();
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                OnUserEarnedRewardListener listener = invocation.getArgument(1);
                listener.onUserEarnedReward(mockRewardItem);
                return null;
              }
            })
        .when(mockAd)
        .show(any(Activity.class), any(OnUserEarnedRewardListener.class));

    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                OnAdMetadataChangedListener listener = invocation.getArgument(0);
                listener.onAdMetadataChanged();
                return null;
              }
            })
        .when(mockAd)
        .setOnAdMetadataChangedListener(any(OnAdMetadataChangedListener.class));

    flutterRewardedAd.show();
    verify(mockAd).setFullScreenContentCallback(any(FullScreenContentCallback.class));
    verify(mockAd).show(eq(mockManager.getActivity()), any(OnUserEarnedRewardListener.class));
    verify(mockAd).setOnAdMetadataChangedListener(any(OnAdMetadataChangedListener.class));
    ArgumentMatcher<ServerSideVerificationOptions> serverSideVerificationOptionsArgumentMatcher =
        new ArgumentMatcher<ServerSideVerificationOptions>() {
          @Override
          public boolean matches(ServerSideVerificationOptions argument) {
            return argument.getCustomData().equals(options.getCustomData())
                && argument.getUserId().equals(options.getUserId());
          }
        };
    verify(mockAd)
        .setServerSideVerificationOptions(
            ArgumentMatchers.argThat(serverSideVerificationOptionsArgumentMatcher));
    verify(mockManager).onAdShowedFullScreenContent(eq(1));
    verify(mockManager).onAdImpression(eq(1));
    verify(mockManager).onAdDismissedFullScreenContent(eq(1));
    verify(mockManager).onRewardedAdUserEarnedReward(1, new FlutterRewardItem(5, "$$"));
    verify(mockManager).onAdMetadataChanged(eq(1));

    assertNull(flutterRewardedAd.getPlatformView());
  }

  @Test
  public void loadRewardedAdWithAdManagerRequest_nullServerSideOptions() {
    final FlutterServerSideVerificationOptions options =
        new FlutterServerSideVerificationOptions(null, null);
    setupAdManagerMocks(options);

    final FlutterRewardedAd mockFlutterAd = spy(flutterRewardedAd);
    final RewardedAd mockAd = mock(RewardedAd.class);
    final LoadAdError loadAdError = new LoadAdError(1, "2", "3", null, null);
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                RewardedAdLoadCallback adLoadCallback = invocation.getArgument(2);
                // Pass back null for ad
                adLoadCallback.onAdLoaded(mockAd);
                return null;
              }
            })
        .when(mockFlutterAdLoader)
        .loadAdManagerRewarded(
            anyString(), any(AdManagerAdRequest.class), any(RewardedAdLoadCallback.class));
    final ResponseInfo responseInfo = mock(ResponseInfo.class);
    doReturn(responseInfo).when(mockAd).getResponseInfo();

    mockFlutterAd.load();

    verify(mockFlutterAdLoader)
        .loadAdManagerRewarded(
            eq("testId"), eq(mockAdManagerAdRequest), any(RewardedAdLoadCallback.class));

    verify(mockManager).onAdLoaded(eq(1), eq(responseInfo));

    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                FullScreenContentCallback callback = invocation.getArgument(0);
                callback.onAdShowedFullScreenContent();
                return null;
              }
            })
        .when(mockAd)
        .setFullScreenContentCallback(any(FullScreenContentCallback.class));

    mockFlutterAd.show();
    verify(mockAd).setFullScreenContentCallback(any(FullScreenContentCallback.class));
    verify(mockAd).show(eq(mockManager.getActivity()), any(OnUserEarnedRewardListener.class));
    verify(mockAd).setOnAdMetadataChangedListener(any(OnAdMetadataChangedListener.class));
    ArgumentMatcher<ServerSideVerificationOptions> serverSideVerificationOptionsArgumentMatcher =
        new ArgumentMatcher<ServerSideVerificationOptions>() {
          @Override
          public boolean matches(ServerSideVerificationOptions argument) {
            return argument.getCustomData().isEmpty() && argument.getUserId().isEmpty();
          }
        };
    verify(mockAd)
        .setServerSideVerificationOptions(
            ArgumentMatchers.argThat(serverSideVerificationOptionsArgumentMatcher));
  }

  @Test
  public void loadRewardedAdFailToShow() {
    setupAdmobMocks(null);

    final RewardedAd mockRewardedAd = mock(RewardedAd.class);
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                RewardedAdLoadCallback adLoadCallback = invocation.getArgument(2);
                // Pass back null for ad
                adLoadCallback.onAdLoaded(mockRewardedAd);
                return null;
              }
            })
        .when(mockFlutterAdLoader)
        .loadRewarded(anyString(), any(AdRequest.class), any(RewardedAdLoadCallback.class));
    final ResponseInfo responseInfo = mock(ResponseInfo.class);
    doReturn(responseInfo).when(mockRewardedAd).getResponseInfo();
    flutterRewardedAd.load();

    verify(mockFlutterAdLoader)
        .loadRewarded(eq("testId"), eq(mockAdRequest), any(RewardedAdLoadCallback.class));

    verify(mockManager).onAdLoaded(eq(1), eq(responseInfo));
    final AdError adError = new AdError(0, "ad", "error");
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                FullScreenContentCallback callback = invocation.getArgument(0);
                callback.onAdFailedToShowFullScreenContent(adError);
                return null;
              }
            })
        .when(mockRewardedAd)
        .setFullScreenContentCallback(any(FullScreenContentCallback.class));

    flutterRewardedAd.show();
    verify(mockRewardedAd).setFullScreenContentCallback(any(FullScreenContentCallback.class));
    verify(mockManager).onFailedToShowFullScreenContent(eq(1), eq(adError));
  }

  @Test
  public void loadRewardedAd_setImmersiveMode() {
    setupAdmobMocks(null);

    final RewardedAd mockRewardedAd = mock(RewardedAd.class);
    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                RewardedAdLoadCallback adLoadCallback = invocation.getArgument(2);
                adLoadCallback.onAdLoaded(mockRewardedAd);
                // Pass back null for ad
                return null;
              }
            })
        .when(mockFlutterAdLoader)
        .loadRewarded(anyString(), any(AdRequest.class), any(RewardedAdLoadCallback.class));

    flutterRewardedAd.load();
    flutterRewardedAd.setImmersiveMode(true);
    verify(mockRewardedAd).setImmersiveMode(true);
  }
}
