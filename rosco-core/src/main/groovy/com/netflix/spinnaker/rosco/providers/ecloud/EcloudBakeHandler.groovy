package com.netflix.spinnaker.rosco.providers.ecloud

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.ecloud.config.RoscoEcloudConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * @description ${description}
 * @author han.pengfei
 * @date 2024-04-23
 */
@Slf4j
@Component
class EcloudBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "ecloud-basic: Trying to create a new image\\("

  private static final String IMAGE_ID_TOKEN = "ecloud-basic: Ecloud images\\("


  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoEcloudConfiguration.EcloudBakeryDefaults ecloudBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return ecloudBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
            cloudProvider: BakeRequest.CloudProviderType.ecloud,
            baseImages: ecloudBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }


  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    log.info("enter scrapeCompletedBakeResults, with $region, $bakeId, $logsContent")
    String amiId
    String imageName
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(IMAGE_NAME_TOKEN).last().split("\\)").first()
      } else if (line =~ IMAGE_ID_TOKEN) {
        amiId = line.split(IMAGE_ID_TOKEN + "$region: ").last().split("\\)").first()
      }
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def operatingSystemVirtualizationSettings = ecloudBakeryDefaults?.baseImages.find {
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
  Map buildParameterMap(String region, Object virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    def parameterMap = [
            ecloud_region       : region,
            ecloud_instance_type: virtualizationSettings.instanceType,
            ecloud_source_image_id   : virtualizationSettings.sourceImageId,
            ecloud_target_image   : imageName
    ]

    if (virtualizationSettings.sshUserName) {
      parameterMap.ecloud_ssh_username = virtualizationSettings.sshUserName
    }

    if (ecloudBakeryDefaults.accessKey && ecloudBakeryDefaults.secretKey) {
      parameterMap.ecloud_access_key = ecloudBakeryDefaults.accessKey
      parameterMap.ecloud_secret_key = ecloudBakeryDefaults.secretKey
    }

    if (ecloudBakeryDefaults.subnetId) {
      parameterMap.ecloud_subnet_id = ecloudBakeryDefaults.subnetId
    }

    if (ecloudBakeryDefaults.vpcId) {
      parameterMap.ecloud_vpc_id = ecloudBakeryDefaults.vpcId
    }

    if (ecloudBakeryDefaults.associatePublicIpAddress != null) {
      parameterMap.ecloud_associate_public_ip_address = ecloudBakeryDefaults.associatePublicIpAddress
    }

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: ecloudBakeryDefaults.templateFile
  }

}
