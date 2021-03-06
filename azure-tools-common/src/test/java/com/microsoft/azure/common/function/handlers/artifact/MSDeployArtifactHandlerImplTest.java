/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.common.function.handlers.artifact;

import com.microsoft.azure.common.deploytarget.DeployTarget;
import com.microsoft.azure.management.appservice.AppSetting;
import com.microsoft.azure.management.appservice.FunctionApp;
import com.microsoft.azure.management.appservice.WebDeployment.DefinitionStages.WithExecute;
import com.microsoft.azure.management.appservice.WebDeployment.DefinitionStages.WithPackageUri;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Map;

import static com.microsoft.azure.common.function.Constants.INTERNAL_STORAGE_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MSDeployArtifactHandlerImpl.class, FunctionArtifactHelper.class})
public class MSDeployArtifactHandlerImplTest {
    private MSDeployArtifactHandlerImpl handler;

    private MSDeployArtifactHandlerImpl handlerSpy;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    public void buildHandler() {
        handler = new MSDeployArtifactHandlerImpl.Builder()
            .stagingDirectoryPath("target/classes")
            .build();
        handlerSpy = spy(handler);
    }

    @Test
    public void publish() throws Exception {
        final DeployTarget deployTarget = mock(DeployTarget.class);
        final Map mapSettings = mock(Map.class);
        final File file = mock(File.class);
        final AppSetting storageSetting = mock(AppSetting.class);

        mapSettings.put(INTERNAL_STORAGE_KEY, storageSetting);
        doReturn(mapSettings).when(deployTarget).getAppSettings();
        doReturn(storageSetting).when(mapSettings).get(anyString());
        buildHandler();

        PowerMockito.mockStatic(FunctionArtifactHelper.class);
        when(FunctionArtifactHelper.getCloudStorageAccount(any())).thenReturn(null);
        doReturn("").when(handlerSpy).uploadPackageToAzureStorage(file, null, "");
        doReturn("").when(handlerSpy).getBlobName();
        doReturn(mapSettings).when(deployTarget).getAppSettings();
        doNothing().when(handlerSpy).deployWithPackageUri(eq(deployTarget), eq(""), any(Runnable.class));
        doReturn(file).when(handlerSpy).createZipPackage();

        handlerSpy.publish(deployTarget);

        verify(handlerSpy, times(1)).publish(deployTarget);
        verify(handlerSpy, times(1)).createZipPackage();
        verify(handlerSpy, times(1)).getBlobName();
        verify(handlerSpy, times(1)).uploadPackageToAzureStorage(file, null, "");
        verify(handlerSpy, times(1)).deployWithPackageUri(eq(deployTarget), eq(""), any(Runnable.class));
        verifyNoMoreInteractions(handlerSpy);
    }

    @Test
    public void createZipPackage() throws Exception {
        buildHandler();
        final File zipPackage = handlerSpy.createZipPackage();

        assertTrue(zipPackage.exists());
    }

    @Test
    public void uploadPackageToAzureStorage() throws Exception {
        final CloudStorageAccount storageAccount = mock(CloudStorageAccount.class);
        final CloudBlobClient blobClient = mock(CloudBlobClient.class);
        doReturn(blobClient).when(storageAccount).createCloudBlobClient();
        final CloudBlobContainer blobContainer = mock(CloudBlobContainer.class);
        doReturn(blobContainer).when(blobClient).getContainerReference(anyString());
        doReturn(true).when(blobContainer)
                .createIfNotExists(any(BlobContainerPublicAccessType.class), isNull(), isNull());
        final CloudBlockBlob blob = mock(CloudBlockBlob.class);
        doReturn(blob).when(blobContainer).getBlockBlobReference(anyString());
        doNothing().when(blob).upload(any(FileInputStream.class), anyLong());
        doReturn(new URI("http://blob")).when(blob).getUri();
        doReturn("token").when(blob).generateSharedAccessSignature(any(), isNull());
        final File file = new File("pom.xml");

        buildHandler();
        final String packageUri = handler.uploadPackageToAzureStorage(file, storageAccount, "blob");

        assertEquals("http://blob?token", packageUri);
    }

    @Test
    public void deployWithPackageUri() throws Exception {
        final FunctionApp app = mock(FunctionApp.class);
        final DeployTarget deployTarget = mock(DeployTarget.class);
        final WithPackageUri withPackageUri = mock(WithPackageUri.class);
        doReturn(withPackageUri).when(app).deploy();
        final WithExecute withExecute = mock(WithExecute.class);
        doReturn(withExecute).when(withPackageUri).withPackageUri(anyString());
        doReturn(withExecute).when(withExecute).withExistingDeploymentsDeleted(false);
        final Runnable runnable = mock(Runnable.class);
        doNothing().when(deployTarget).msDeploy("uri", false);
        buildHandler();
        handlerSpy.deployWithPackageUri(deployTarget, "uri", runnable);

        verify(handlerSpy, times(1)).deployWithPackageUri(deployTarget, "uri", runnable);
        verify(runnable, times(1)).run();
        verify(deployTarget, times(1)).msDeploy("uri", false);
    }

    @Test
    public void deletePackageFromAzureStorage() throws Exception {
        final CloudStorageAccount storageAccount = mock(CloudStorageAccount.class);
        final CloudBlobClient blobClient = mock(CloudBlobClient.class);
        doReturn(blobClient).when(storageAccount).createCloudBlobClient();
        final CloudBlobContainer blobContainer = mock(CloudBlobContainer.class);
        doReturn(blobContainer).when(blobClient).getContainerReference(anyString());
        doReturn(true).when(blobContainer).exists();
        final CloudBlockBlob blob = mock(CloudBlockBlob.class);
        doReturn(blob).when(blobContainer).getBlockBlobReference(anyString());
        doReturn(true).when(blob).deleteIfExists();
        buildHandler();
        handler.deletePackageFromAzureStorage(storageAccount, "blob");
    }
}
