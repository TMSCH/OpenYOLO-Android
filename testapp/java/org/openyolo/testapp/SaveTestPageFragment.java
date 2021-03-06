/*
 * Copyright 2017 The OpenYOLO Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openyolo.testapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import org.openyolo.api.CredentialClient;
import org.openyolo.protocol.Credential;
import org.openyolo.protocol.CredentialSaveRequest;
import org.openyolo.protocol.CredentialSaveResult;

/**
 * Fragment which contains a method of testing the OpenYolo credential save flow.
 */
public final class SaveTestPageFragment extends TestPageFragment {

    private static final int RC_SAVE = 0;

    @BindView(R.id.save_credential)
    CredentialView mCredentialView;

    private CredentialClient mApi;

    public String getPageTitle() {
        return "Save";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApi = CredentialClient.getApplicationBoundInstance(getActivity());
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.save_test_layout, container, false);

        ButterKnife.bind(this, view);
        mCredentialView.setEnableInputGeneration(true);

        return view;
    }

    @OnClick(R.id.save_button)
    void onSave() {
        final Credential credential = mCredentialView.makeCredentialFromFields();
        final CredentialSaveRequest request = CredentialSaveRequest.fromCredential(credential);
        final Intent saveIntent = mApi.getSaveIntent(request);

        if (saveIntent == null) {
            showSnackbar(R.string.no_available_save_providers);
            return;
        }

        startActivityForResult(saveIntent, RC_SAVE);
    }

    private void showSnackbar(@StringRes int messageId) {
        Snackbar.make(getView(), messageId, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != RC_SAVE) {
            showSnackbar(R.string.unknown_response);
            return;
        }

        CredentialSaveResult result = mApi.getCredentialSaveResult(data);

        int resultMessageId;
        switch (result.getResultCode()) {
            case CredentialSaveResult.CODE_SAVED:
                resultMessageId = R.string.save_result_saved;
                break;
            case CredentialSaveResult.CODE_BAD_REQUEST:
                resultMessageId = R.string.save_result_bad_request;
                break;
            case CredentialSaveResult.CODE_PROVIDER_REFUSED:
                resultMessageId = R.string.save_result_provider_refused;
                break;
            case CredentialSaveResult.CODE_USER_CANCELED:
                resultMessageId = R.string.save_result_user_canceled;
                break;
            case CredentialSaveResult.CODE_USER_REFUSED:
                resultMessageId = R.string.save_result_user_refused;
                break;
            default:
                resultMessageId = R.string.save_result_unkown;
        }

        showSnackbar(resultMessageId);
    }
}
