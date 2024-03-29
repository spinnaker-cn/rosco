/*
 * Copyright 2019 he Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.rosco.providers.hecloud

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.hecloud.config.RoscoHeCloudConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class HeCloudBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = 'chinamobilecloud-ecs: Creating the image: '
  private static final String IMAGE_ID_TOKEN = 'chinamobilecloud-ecs: An image was created: '

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoHeCloudConfiguration.HeCloudBakeryDefaults hecloudBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return hecloudBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.hecloud,
      baseImages: hecloudBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    region
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def operatingSystemVirtualizationSettings = hecloudBakeryDefaults?.baseImages.find {
      it.baseImage.id == bakeRequest.base_os
    }

    if (!operatingSystemVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def virtualizationSettings = operatingSystemVirtualizationSettings?.virtualizationSettings.find {
      it.region == region
    }

    if (!virtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region' and operating system '$bakeRequest.base_os'.")
    }

    if (bakeRequest.base_ami) {
      virtualizationSettings = virtualizationSettings.clone()
      virtualizationSettings.sourceImageId = bakeRequest.base_ami
    }

    return virtualizationSettings
  }

  @Override
  Map buildParameterMap(String region, def virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    def parameterMap = [
      hecloud_region: region,
      hecloud_image_name: imageName,
      hecloud_flavor_id: virtualizationSettings.flavorId,
      hecloud_source_image: virtualizationSettings.sourceImageId,
      hecloud_ssh_username: virtualizationSettings.sshUserName
    ]

    if (hecloudBakeryDefaults.accessKey && hecloudBakeryDefaults.secretKey) {
      parameterMap.hecloud_access_key = hecloudBakeryDefaults.accessKey
      parameterMap.hecloud_secret_key = hecloudBakeryDefaults.secretKey
    }

    if (hecloudBakeryDefaults.vpcId) {
      parameterMap.hecloud_vpc_id = hecloudBakeryDefaults.vpcId
    }

    if (hecloudBakeryDefaults.subnetId) {
      parameterMap.hecloud_subnet_id = hecloudBakeryDefaults.subnetId
    }

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: hecloudBakeryDefaults.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String amiId
    String imageName

    // TODO(duftler): Presently scraping the logs for the image name/id. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis and querying oort for amiId from amiName.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(IMAGE_NAME_TOKEN).last()
      } else if (line =~ IMAGE_ID_TOKEN) {
        amiId = line.split(IMAGE_ID_TOKEN).last()
      }
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }

  @Override
  List<String> getMaskedPackerParameters() {
    ['hecloud_secret_key']
  }

}
