/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.providers.alicloud

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.alicloud.config.RoscoAliCloudConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class AliCloudBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "alicloud-ecs: Creating image:"

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoAliCloudConfiguration.AliCloudBakeryDefaults alicloudBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return alicloudBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.alicloud,
      baseImages: alicloudBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return "$region"
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def virtualizationSettings = alicloudBakeryDefaults?.baseImages?.find {
      it.baseImage.id == bakeRequest.base_os
    }

    if (!virtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def alicloudVirtualizationSettings = virtualizationSettings?.virtualizationSettings.find {
      it.region == region
    }

    if (!alicloudVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region', operating system '$bakeRequest.base_os'.")
    }

    if (bakeRequest.base_ami) {
      alicloudVirtualizationSettings = alicloudVirtualizationSettings.clone()
      alicloudVirtualizationSettings.sourceImage = bakeRequest.base_ami
    }

    return alicloudVirtualizationSettings
  }

  @Override
  Map buildParameterMap(String region, def alicloudVirtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    def parameterMap = [
      alicloud_region       : region,
      alicloud_instance_type: alicloudVirtualizationSettings.instanceType,
      alicloud_source_image : alicloudVirtualizationSettings.sourceImage,
      alicloud_target_image : imageName
    ]

    if (alicloudVirtualizationSettings.sshUserName) {
      parameterMap.alicloud_ssh_username = alicloudVirtualizationSettings.sshUserName
    }

    if (alicloudBakeryDefaults.alicloudAccessKey && alicloudBakeryDefaults.alicloudSecretKey) {
      parameterMap.alicloud_access_key = alicloudBakeryDefaults.alicloudAccessKey
      parameterMap.alicloud_secret_key = alicloudBakeryDefaults.alicloudSecretKey
    }

    if (alicloudBakeryDefaults.alicloudVSwitchId) {
      parameterMap.alicloud_vswitch_id = alicloudBakeryDefaults.alicloudVSwitchId
    }

    if (alicloudBakeryDefaults.alicloudVpcId) {
      parameterMap.alicloud_vpc_id = alicloudBakeryDefaults.alicloudVpcId
    }

    if (bakeRequest.build_info_url) {
      parameterMap.build_info_url = bakeRequest.build_info_url
    }

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: alicloudBakeryDefaults.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String amiId
    String imageName

    // TODO(duftler): Presently scraping the logs for the image name/id. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis and querying oort for amiId from amiName.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(" ").last()
      } else if (line =~ "$region:") {
        amiId = line.split(" ").last()
      }
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }
}
