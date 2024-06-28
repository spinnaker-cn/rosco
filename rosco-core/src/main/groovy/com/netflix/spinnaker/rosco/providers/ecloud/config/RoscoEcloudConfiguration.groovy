package com.netflix.spinnaker.rosco.providers.ecloud.config

import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.ecloud.EcloudBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import groovy.transform.AutoClone
import groovy.transform.AutoCloneStyle
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

/**
 * @description ${description}
 * @author han.pengfei
 * @date 2024-04-23
 */
@Slf4j
@Configuration
@ConditionalOnProperty('ecloud.enabled')
@ComponentScan('com.netflix.spinnaker.rosco.providers.ecloud')
class RoscoEcloudConfiguration {
  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  EcloudBakeHandler ecloudBakeHandler

  @Bean
  @ConfigurationProperties('ecloud.bakeryDefaults')
  EcloudBakeryDefaults tencentBakeryDefaults(@Value('${ecloud.bakeryDefaults.defaultVirtualizationType:hvm}') BakeRequest.VmType defaultVirtualizationType) {
    new EcloudBakeryDefaults(defaultVirtualizationType: defaultVirtualizationType)
  }

  static class EcloudBakeryDefaults {
    String accessKey
    String secretKey
    String subnetId
    String vpcId
    Boolean associatePublicIpAddress
    String templateFile
    BakeRequest.VmType defaultVirtualizationType
    List<EcloudOperatingSystemVirtualizationSettings> baseImages = []
  }

  static class EcloudOperatingSystemVirtualizationSettings {
    BakeOptions.BaseImage baseImage
    List<EcloudVirtualizationSettings> virtualizationSettings = []
  }

  @AutoClone(style = AutoCloneStyle.SIMPLE)
  static class EcloudVirtualizationSettings {
    String region
    String zone
//    BakeRequest.VmType virtualizationType
    String instanceType
    String sourceImageId
    String imageName
    String sshUserName
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.ecloud, ecloudBakeHandler)
  }
}
