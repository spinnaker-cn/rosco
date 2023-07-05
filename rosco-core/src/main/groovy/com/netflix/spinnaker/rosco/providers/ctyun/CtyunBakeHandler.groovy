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

package com.netflix.spinnaker.rosco.providers.ctyun

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.ctyun.config.RoscoCtyunConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
public class CtyunBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "A VMImage was created:"

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoCtyunConfiguration.CtyunBakeryDefaults ctyunBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return ctyunBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.ctyun,
      baseImages: ctyunBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
//    if (!bakeRequest.vm_type) {
//      bakeRequest = bakeRequest.copyWith(vm_type: ctyunBakeryDefaults.defaultVirtualizationType)
//    }
//
//    bakeRequest.with {
//      def enhancedNetworkingSegment = enhanced_networking ? 'enhancedNWEnabled' : 'enhancedNWDisabled'
//
//      return "$region:$vm_type:$enhancedNetworkingSegment"
//    }
    return null
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
//    BakeRequest.VmType vmType = bakeRequest.vm_type ?: ctyunBakeryDefaults.defaultVirtualizationType
//
    def operatingSystemVirtualizationSettings = ctyunBakeryDefaults?.baseImages.find {
      it.baseImage.id == bakeRequest.base_os
    }

    if (!operatingSystemVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def virtualizationSettings = operatingSystemVirtualizationSettings?.virtualizationSettings.find {
      it.region == region //&& it.virtualizationType == vmType
    }

    if (!virtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region', operating system '$bakeRequest.base_os', and vm type '$vmType'.")
    }

    if (bakeRequest.base_ami) {
      virtualizationSettings = virtualizationSettings.clone()
      virtualizationSettings.sourceImageId = bakeRequest.base_ami
    }

    return virtualizationSettings
  }

  @Override
  Map buildParameterMap(String region, def virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    String[] strs = imageName.split("-")
    StringBuffer str = new StringBuffer()
    str.append(strs[0]).append("-").append(strs[2].substring(2))
    def newImageName = str.toString()
    def parameterMap = [
      ctyun_client_token: UUID.randomUUID().toString(),
      ctyun_region_id       : region,
      ctyun_instance_type: virtualizationSettings.instanceType,
      ctyun_source_image_id   : virtualizationSettings.sourceImageId,
      ctyun_target_image   : newImageName
    ]

    if (virtualizationSettings.sshUserName) {
      parameterMap.ctyun_ssh_username = virtualizationSettings.sshUserName
    }

    if (ctyunBakeryDefaults.access_key && ctyunBakeryDefaults.secret_key) {
      parameterMap.ctyun_access_key = ctyunBakeryDefaults.access_key
      parameterMap.ctyun_secret_key = ctyunBakeryDefaults.secret_key
    }

    if (ctyunBakeryDefaults.subnetId) {
      parameterMap.ctyun_subnet_id = ctyunBakeryDefaults.subnetId
    }

    if (ctyunBakeryDefaults.vpcId) {
      parameterMap.ctyun_vpc_id = ctyunBakeryDefaults.vpcId
    }

    if (ctyunBakeryDefaults.associatePublicIpAddress != null) {
      parameterMap.ctyun_associate_public_ip_address = ctyunBakeryDefaults.associatePublicIpAddress
    }

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: ctyunBakeryDefaults.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    log.info("enter scrapeCompletedBakeResults, with $region, $bakeId, $logsContent")
    String amiId
    String imageName

    // TODO(duftler): Presently scraping the logs for the image name/id. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis and querying oort for amiId from amiName.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        String imageIdName = line.split(IMAGE_NAME_TOKEN).last()
        String[] imgs = imageIdName.split(",")
        amiId = imgs.first()
        imageName = imgs.last()
      }
      /*else if (line =~ "$region:") {
        amiId = line.split("ctyun-ecs: Ctyun images\\($region: ").last().split("\\)").first()
      }*/
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }
}
