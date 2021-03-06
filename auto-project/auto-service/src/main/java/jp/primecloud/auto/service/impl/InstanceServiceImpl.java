/*
 * Copyright 2014 by SCSK Corporation.
 * 
 * This file is part of PrimeCloud Controller(TM).
 * 
 * PrimeCloud Controller(TM) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * 
 * PrimeCloud Controller(TM) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with PrimeCloud Controller(TM). If not, see <http://www.gnu.org/licenses/>.
 */
package jp.primecloud.auto.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jp.primecloud.auto.common.component.PasswordGenerator;
import jp.primecloud.auto.common.status.ComponentInstanceStatus;
import jp.primecloud.auto.common.status.InstanceCoodinateStatus;
import jp.primecloud.auto.common.status.InstanceStatus;
import jp.primecloud.auto.common.status.ZabbixInstanceStatus;
import jp.primecloud.auto.config.Config;
import jp.primecloud.auto.entity.crud.AwsAddress;
import jp.primecloud.auto.entity.crud.AwsCertificate;
import jp.primecloud.auto.entity.crud.AwsInstance;
import jp.primecloud.auto.entity.crud.AwsVolume;
import jp.primecloud.auto.entity.crud.CloudstackAddress;
import jp.primecloud.auto.entity.crud.CloudstackCertificate;
import jp.primecloud.auto.entity.crud.CloudstackInstance;
import jp.primecloud.auto.entity.crud.CloudstackVolume;
import jp.primecloud.auto.entity.crud.Component;
import jp.primecloud.auto.entity.crud.ComponentInstance;
import jp.primecloud.auto.entity.crud.ComponentType;
import jp.primecloud.auto.entity.crud.Farm;
import jp.primecloud.auto.entity.crud.Image;
import jp.primecloud.auto.entity.crud.ImageAws;
import jp.primecloud.auto.entity.crud.ImageCloudstack;
import jp.primecloud.auto.entity.crud.ImageNifty;
import jp.primecloud.auto.entity.crud.ImageVmware;
import jp.primecloud.auto.entity.crud.Instance;
import jp.primecloud.auto.entity.crud.InstanceConfig;
import jp.primecloud.auto.entity.crud.LoadBalancer;
import jp.primecloud.auto.entity.crud.NiftyInstance;
import jp.primecloud.auto.entity.crud.NiftyKeyPair;
import jp.primecloud.auto.entity.crud.Platform;
import jp.primecloud.auto.entity.crud.PlatformAws;
import jp.primecloud.auto.entity.crud.PlatformCloudstack;
import jp.primecloud.auto.entity.crud.PlatformNifty;
import jp.primecloud.auto.entity.crud.PlatformVmware;
import jp.primecloud.auto.entity.crud.PuppetInstance;
import jp.primecloud.auto.entity.crud.VmwareAddress;
import jp.primecloud.auto.entity.crud.VmwareDisk;
import jp.primecloud.auto.entity.crud.VmwareInstance;
import jp.primecloud.auto.entity.crud.VmwareKeyPair;
import jp.primecloud.auto.entity.crud.ZabbixData;
import jp.primecloud.auto.entity.crud.ZabbixInstance;
import jp.primecloud.auto.exception.AutoApplicationException;
import jp.primecloud.auto.exception.AutoException;
import jp.primecloud.auto.log.EventLogLevel;
import jp.primecloud.auto.log.EventLogger;
import jp.primecloud.auto.service.IaasDescribeService;
import jp.primecloud.auto.service.InstanceService;
import jp.primecloud.auto.service.ServiceSupport;
import jp.primecloud.auto.service.VmwareDescribeService;
import jp.primecloud.auto.service.dto.ComponentInstanceDto;
import jp.primecloud.auto.service.dto.ImageDto;
import jp.primecloud.auto.service.dto.InstanceDto;
import jp.primecloud.auto.service.dto.KeyPairDto;
import jp.primecloud.auto.service.dto.PlatformDto;
import jp.primecloud.auto.service.dto.SecurityGroupDto;
import jp.primecloud.auto.service.dto.SubnetDto;
import jp.primecloud.auto.service.dto.VmwareAddressDto;
import jp.primecloud.auto.service.dto.ZoneDto;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;

import com.amazonaws.services.ec2.model.InstanceType;
import jp.primecloud.auto.component.mysql.MySQLConstants;
import jp.primecloud.auto.iaasgw.IaasGatewayFactory;
import jp.primecloud.auto.iaasgw.IaasGatewayWrapper;
import jp.primecloud.auto.process.nifty.NiftyProcessClient;
import jp.primecloud.auto.process.nifty.NiftyProcessClientFactory;
import jp.primecloud.auto.process.vmware.VmwareDiskProcess;
import jp.primecloud.auto.process.vmware.VmwareMachineProcess;
import jp.primecloud.auto.process.vmware.VmwareProcessClient;
import jp.primecloud.auto.process.vmware.VmwareProcessClientFactory;
import jp.primecloud.auto.process.zabbix.ZabbixHostProcess;
import jp.primecloud.auto.process.zabbix.ZabbixProcessClient;
import jp.primecloud.auto.process.zabbix.ZabbixProcessClientFactory;
import com.vmware.vim25.mo.ComputeResource;

/**
 * <p>
 * InstanceServiceインターフェースの実装クラス
 * </p>
 *
 */
public class InstanceServiceImpl extends ServiceSupport implements InstanceService {

    protected IaasGatewayFactory iaasGatewayFactory;

    protected IaasDescribeService iaasDescribeService;

    protected VmwareDescribeService vmwareDescribeService;

    protected VmwareMachineProcess vmwareMachineProcess;

    protected VmwareDiskProcess vmwareDiskProcess;

    protected VmwareProcessClientFactory vmwareProcessClientFactory;

    protected NiftyProcessClientFactory niftyProcessClientFactory;

    protected ZabbixProcessClientFactory zabbixProcessClientFactory;

    protected ZabbixHostProcess zabbixHostProcess;

    protected PasswordGenerator passwordGenerator = new PasswordGenerator();

    protected EventLogger eventLogger;

    /**
     * {@inheritDoc}
     */
    @Override
    public InstanceDto getInstance(Long instanceNo) {
        // インスタンスを取得
        Instance instance = instanceDao.read(instanceNo);

        // プラットフォーム取得
        Platform platform = platformDao.read(instance.getPlatformNo());

        // イメージ取得
        Image image = imageDao.read(instance.getImageNo());


        // インスタンスに関連付けられたコンポーネントを取得
        Map<Long, List<ComponentInstance>> componentInstanceMap = new LinkedHashMap<Long, List<ComponentInstance>>();
        componentInstanceMap.put(instanceNo, new ArrayList<ComponentInstance>());
        List<ComponentInstance> tmpComponentInstances = componentInstanceDao.readByInstanceNo(instanceNo);
        for (ComponentInstance componentInstance : tmpComponentInstances) {
            // 関連付けが無効で停止している場合は除外
            if (BooleanUtils.isNotTrue(componentInstance.getAssociate())) {
                ComponentInstanceStatus status = ComponentInstanceStatus.fromStatus(componentInstance.getStatus());
                if (status == ComponentInstanceStatus.STOPPED) {
                    continue;
                }
            }
            componentInstanceMap.get(componentInstance.getInstanceNo()).add(componentInstance);
        }

        // コンポーネントを取得
        Map<Long, Component> componentMap = new HashMap<Long, Component>();
        Set<Long> componentNos = new HashSet<Long>();
        for (ComponentInstance componentInstance : tmpComponentInstances) {
            componentNos.add(componentInstance.getComponentNo());
        }
        List<Component> components = componentDao.readInComponentNos(componentNos);
        for (Component component : components) {
            componentMap.put(component.getComponentNo(), component);
        }

        // インスタンスに紐づく情報を取得
        Farm farm = farmDao.read(instance.getFarmNo());
        PlatformDto platformDto = new PlatformDto();
        platformDto.setPlatform(platform);
        ImageDto imageDto = new ImageDto();
        imageDto.setImage(image);

        AwsInstance awsInstance = null;
        AwsAddress awsAddress = null;
        List<AwsVolume> awsVolumes = null;

        CloudstackInstance cloudstackInstance = null;
        CloudstackAddress cloudstackAddress = null;
        List<CloudstackVolume> cloudstackVolumes = null;

        VmwareInstance vmwareInstance = null;
        VmwareAddress vmwareAddress = null;
        VmwareKeyPair vmwareKeyPair = null;
        List<VmwareDisk> vmwareDisks = null;

        NiftyInstance niftyInstance = null;
        NiftyKeyPair niftyKeyPair = null;

        AwsCertificate awsCertificate = null;

        if ("aws".equals(platform.getPlatformType())) {
            PlatformAws platformAws = platformAwsDao.read(platform.getPlatformNo());
            ImageAws imageAws = imageAwsDao.read(instance.getImageNo());
            platformDto.setPlatformAws(platformAws);
            imageDto.setImageAws(imageAws);

            // AWSインスタンスを取得
            awsInstance = awsInstanceDao.read(instanceNo);

            // AWSアドレスを取得
            List<AwsAddress> awsAddresses = awsAddressDao.readByInstanceNo(instanceNo);
            for (AwsAddress address : awsAddresses) {
                awsAddress  = address;
                break;
            }

            // AWSボリュームを取得
            awsVolumes = awsVolumeDao.readByInstanceNo(instanceNo);

            // AWS認証情報を取得
            awsCertificate = awsCertificateDao.read(farm.getUserNo(), instance.getPlatformNo());

        } else if ("cloudstack".equals(platform.getPlatformType())) {
            PlatformCloudstack platformCloudstack = platformCloudstackDao.read(platform.getPlatformNo());
            ImageCloudstack imageCloudstack = imageCloudstackDao.read(instance.getImageNo());
            platformDto.setPlatformCloudstack(platformCloudstack);
            imageDto.setImageCloudstack(imageCloudstack);

            // CloudStackインスタンスを取得
            cloudstackInstance = cloudstackInstanceDao.read(instanceNo);

            // CloudStackアドレスを取得
            List<CloudstackAddress> cloudstackAddresses = cloudstackAddressDao.readByInstanceNo(instanceNo);
            for (CloudstackAddress address : cloudstackAddresses) {
                cloudstackAddress = address;
                break;
            }

            // CloudStackボリュームを取得
            cloudstackVolumes = cloudstackVolumeDao.readByInstanceNo(instanceNo);


        } else if ("vmware".equals(platform.getPlatformType())) {
            PlatformVmware platformVmware = platformVmwareDao.read(platform.getPlatformNo());
            ImageVmware imageVmware = imageVmwareDao.read(instance.getImageNo());
            platformDto.setPlatformVmware(platformVmware);
            imageDto.setImageVmware(imageVmware);

            // VMwareインスタンスを取得
            vmwareInstance = vmwareInstanceDao.read(instanceNo);

            // VMwareAddressを取得
            vmwareAddress = vmwareAddressDao.readByInstanceNo(instanceNo);

            // VMwareキーペアを取得
            vmwareKeyPair = vmwareKeyPairDao.read(vmwareInstance.getKeyPairNo());

            // VMwareディスクを取得
            vmwareDisks = vmwareDiskDao.readByInstanceNo(instanceNo);

        } else if ("nifty".equals(platform.getPlatformType())) {
            PlatformNifty platformNiftie = platformNiftyDao.read(platform.getPlatformNo());
            ImageNifty imageNiftie = imageNiftyDao.read(instance.getImageNo());
            platformDto.setPlatformNifty(platformNiftie);
            imageDto.setImageNifty(imageNiftie);

            // Niftyインスタンスを取得
            niftyInstance = niftyInstanceDao.read(instanceNo);

            // Niftyキーペアを取得
            niftyKeyPair = niftyKeyPairDao.read(niftyInstance.getKeyPairNo());
        }

        List<InstanceConfig> instanceConfigs = instanceConfigDao.readByInstanceNo(instance.getInstanceNo());

        List<ComponentInstanceDto> componentInstances = new ArrayList<ComponentInstanceDto>();
        for (ComponentInstance componentInstance : componentInstanceMap.get(instance.getInstanceNo())) {
            ComponentInstanceDto componentInstanceDto = new ComponentInstanceDto();
            componentInstanceDto.setComponentInstance(componentInstance);

            Component component = componentMap.get(componentInstance.getComponentNo());
            String url = createUrl(instance.getPublicIp(), component.getComponentTypeNo());
            componentInstanceDto.setUrl(url);
            componentInstances.add(componentInstanceDto);
        }

        // 有効無効に応じてステータスを変更する（画面表示用）
        InstanceStatus instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
        if (BooleanUtils.isTrue(instance.getEnabled())) {
            if (instanceStatus == InstanceStatus.STOPPED) {
                instance.setStatus(InstanceStatus.STARTING.toString());
            }
        } else {
            if (instanceStatus == InstanceStatus.RUNNING || instanceStatus == InstanceStatus.WARNING) {
                instance.setStatus(InstanceStatus.STOPPING.toString());
            }
        }

        // 画面表示用にステータスの変更
        //    サーバステータス 協調設定ステータス   変換後サーバステータス
        //        Running         Coodinating            Configuring
        //        Running         Warning                Warning
        instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
        InstanceCoodinateStatus insCoodiStatus = InstanceCoodinateStatus.fromStatus(instance.getCoodinateStatus());
        // サーバステータス(Running)かつ協調設定ステータス(Coodinating)⇒「Configuring」
        if (instanceStatus == InstanceStatus.RUNNING && insCoodiStatus == InstanceCoodinateStatus.COODINATING) {
            instance.setStatus(InstanceStatus.CONFIGURING.toString());
        // サーバステータス(Running)かつ協調設定ステータス(Warning)⇒「Warning」
        } else if (instanceStatus == InstanceStatus.RUNNING && insCoodiStatus == InstanceCoodinateStatus.WARNING) {
            instance.setStatus(InstanceStatus.WARNING.toString());
        }

        // インスタンスごとのコンポーネントのステータスを調整する（画面表示用）
        instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
        for (ComponentInstanceDto componentInstanceDto : componentInstances) {
            ComponentInstance componentInstance = componentInstanceDto.getComponentInstance();
            ComponentInstanceStatus status = ComponentInstanceStatus.fromStatus(componentInstance.getStatus());
            if (BooleanUtils.isTrue(componentInstance.getEnabled())) {
                if (status == ComponentInstanceStatus.STOPPED) {
                    if (instanceStatus == InstanceStatus.WARNING) {
                        // インスタンスがWaringであれば、コンポーネントもWarningとする
                        componentInstance.setStatus(ComponentInstanceStatus.WARNING.toString());
                    } else if (BooleanUtils.isTrue(farm.getScheduled())) {
                        componentInstance.setStatus(ComponentInstanceStatus.STARTING.toString());
                    }
                }
            } else {
                if (status == ComponentInstanceStatus.RUNNING || status == ComponentInstanceStatus.WARNING) {
                    if (BooleanUtils.isTrue(farm.getScheduled())) {
                        // ファームが処理対象であれば、Stoppingにする
                        componentInstance.setStatus(ComponentInstanceStatus.STOPPING.toString());
                    }
                }
            }
        }

        // ソート
        Collections.sort(componentInstances, Comparators.COMPARATOR_COMPONENT_INSTANCE_DTO);

        //戻り値格納
        InstanceDto dto = new InstanceDto();
        dto.setInstance(instance);
        dto.setPlatform(platformDto);
        dto.setImage(imageDto);
        dto.setInstanceConfigs(instanceConfigs);
        dto.setComponentInstances(componentInstances);
        dto.setAwsInstance(awsInstance);
        dto.setAwsAddress(awsAddress);
        dto.setAwsVolumes(awsVolumes);
        dto.setAwsCertificate(awsCertificate);
        dto.setCloudstackInstance(cloudstackInstance);
        dto.setCloudstackAddress(cloudstackAddress);
        dto.setCloudstackVolumes(cloudstackVolumes);
        dto.setVmwareInstance(vmwareInstance);
        dto.setVmwareAddress(vmwareAddress);
        dto.setVmwareKeyPair(vmwareKeyPair);
        dto.setVmwareDisks(vmwareDisks);
        dto.setNiftyInstance(niftyInstance);
        dto.setNiftyKeyPair(niftyKeyPair);

        return dto;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public List<InstanceDto> getInstances(Long farmNo) {
        // インスタンスを取得
        List<Instance> instances = new ArrayList<Instance>();
        List<Instance> allInstances = instanceDao.readByFarmNo(farmNo);
        for (Instance instance : allInstances) {
            // ロードバランサインスタンスは除外する
            if (BooleanUtils.isTrue(instance.getLoadBalancer())) {
                continue;
            }
            instances.add(instance);
        }

        // コンポーネント番号のリスト
        List<Long> instanceNos = new ArrayList<Long>();
        for (Instance instance : instances) {
            instanceNos.add(instance.getInstanceNo());
        }

        // プラットフォーム取得
        List<Platform> platforms = platformDao.readAll();
        Map<Long, Platform> platformMap = new LinkedHashMap<Long, Platform>();
        for (Platform platform: platforms) {
            platformMap.put(platform.getPlatformNo(), platform);
        }

        // プラットフォーム(AWS)取得
        List<PlatformAws> platformAwss = platformAwsDao.readAll();
        Map<Long, PlatformAws> platformAwsMap = new LinkedHashMap<Long, PlatformAws>();
        for (PlatformAws platformAws: platformAwss) {
            platformAwsMap.put(platformAws.getPlatformNo(), platformAws);
        }

        // プラットフォーム(VMWare)取得
        List<PlatformVmware> platformVmwares = platformVmwareDao.readAll();
        Map<Long, PlatformVmware> platformVmwareMap = new LinkedHashMap<Long, PlatformVmware>();
        for (PlatformVmware platformVmware: platformVmwares) {
            platformVmwareMap.put(platformVmware.getPlatformNo(), platformVmware);
        }

        // プラットフォーム(CloudStack)取得
        List<PlatformCloudstack> platformCloudstacks = platformCloudstackDao.readAll();
        Map<Long, PlatformCloudstack> platformCloudstackMap = new LinkedHashMap<Long, PlatformCloudstack>();
        for (PlatformCloudstack platformCloudstack: platformCloudstacks) {
            platformCloudstackMap.put(platformCloudstack.getPlatformNo(), platformCloudstack);
        }

        // プラットフォーム(Nifty)取得
        List<PlatformNifty> platformNifties = platformNiftyDao.readAll();
        Map<Long, PlatformNifty> platformNiftyMap = new LinkedHashMap<Long, PlatformNifty>();
        for (PlatformNifty platformNifty: platformNifties) {
            platformNiftyMap.put(platformNifty.getPlatformNo(), platformNifty);
        }

        // イメージ取得
        List<Image> images = imageDao.readAll();
        Map<Long, Image> imageMap = new LinkedHashMap<Long, Image>();
        for (Image image: images) {
            imageMap.put(image.getImageNo(), image);
        }

        // イメージ(AWS)取得
        List<ImageAws> imageAwss = imageAwsDao.readAll();
        Map<Long, ImageAws> imageAwsMap = new LinkedHashMap<Long, ImageAws>();
        for (ImageAws imageAws: imageAwss) {
            imageAwsMap.put(imageAws.getImageNo(), imageAws);
        }

        // イメージ(VMware)取得
        List<ImageVmware> imageVmwares = imageVmwareDao.readAll();
        Map<Long, ImageVmware> imageVmwareMap = new LinkedHashMap<Long, ImageVmware>();
        for (ImageVmware imageVmware: imageVmwares) {
            imageVmwareMap.put(imageVmware.getImageNo(), imageVmware);
        }

        // イメージ(CloudStack)取得
        List<ImageCloudstack> imageCloudstacks = imageCloudstackDao.readAll();
        Map<Long, ImageCloudstack> imageCloudstackMap = new LinkedHashMap<Long, ImageCloudstack>();
        for (ImageCloudstack imageCloudstack: imageCloudstacks) {
            imageCloudstackMap.put(imageCloudstack.getImageNo(), imageCloudstack);
        }

        // イメージ(Nifty)取得
        List<ImageNifty> imageNifties = imageNiftyDao.readAll();
        Map<Long, ImageNifty> imageNiftyMap = new LinkedHashMap<Long, ImageNifty>();
        for (ImageNifty imageNifty: imageNifties) {
            imageNiftyMap.put(imageNifty.getImageNo(), imageNifty);
        }

        // インスタンスに関連付けられたコンポーネントを取得
        Map<Long, List<ComponentInstance>> componentInstanceMap = new LinkedHashMap<Long, List<ComponentInstance>>();
        for (Long instanceNo : instanceNos) {
            componentInstanceMap.put(instanceNo, new ArrayList<ComponentInstance>());
        }
        List<ComponentInstance> tmpComponentInstances = componentInstanceDao.readInInstanceNos(instanceNos);
        for (ComponentInstance componentInstance : tmpComponentInstances) {
            // 関連付けが無効で停止している場合は除外
            if (BooleanUtils.isNotTrue(componentInstance.getAssociate())) {
                ComponentInstanceStatus status = ComponentInstanceStatus.fromStatus(componentInstance.getStatus());
                if (status == ComponentInstanceStatus.STOPPED) {
                    continue;
                }
            }
            componentInstanceMap.get(componentInstance.getInstanceNo()).add(componentInstance);
        }

        // コンポーネントを取得
        Map<Long, Component> componentMap = new HashMap<Long, Component>();
        Set<Long> componentNos = new HashSet<Long>();
        for (ComponentInstance componentInstance : tmpComponentInstances) {
            componentNos.add(componentInstance.getComponentNo());
        }
        List<Component> components = componentDao.readInComponentNos(componentNos);
        for (Component component : components) {
            componentMap.put(component.getComponentNo(), component);
        }

        // AWSインスタンスを取得
        List<AwsInstance> awsInstances = awsInstanceDao.readInInstanceNos(instanceNos);
        Map<Long, AwsInstance> awsInstanceMap = new LinkedHashMap<Long, AwsInstance>();
        for (AwsInstance awsInstance : awsInstances) {
            awsInstanceMap.put(awsInstance.getInstanceNo(), awsInstance);
        }

        // AWSアドレスを取得
        Farm farm = farmDao.read(farmNo);
        List<AwsAddress> awsAddresses = awsAddressDao.readByUserNo(farm.getUserNo());
        Map<Long, AwsAddress> awsAddressMap = new LinkedHashMap<Long, AwsAddress>();
        for (AwsAddress awsAddress : awsAddresses) {
            if (awsAddress.getInstanceNo() != null) {
                awsAddressMap.put(awsAddress.getInstanceNo(), awsAddress);
            }
        }

        // AWSボリュームを取得
        List<AwsVolume> farmAwsVolumes = awsVolumeDao.readByFarmNo(farmNo);
        Map<Long, List<AwsVolume>> awsVolumesMap = new LinkedHashMap<Long, List<AwsVolume>>();
        for (AwsVolume awsVolume : farmAwsVolumes) {
            Long instanceNo = awsVolume.getInstanceNo();
            if (instanceNo != null) {
                List<AwsVolume> awsVolumes = awsVolumesMap.get(instanceNo);
                if (awsVolumes == null) {
                    awsVolumes = new ArrayList<AwsVolume>();
                    awsVolumesMap.put(instanceNo, awsVolumes);
                }
                awsVolumes.add(awsVolume);
            }
        }

        // CloudStackインスタンスを取得
        List<CloudstackInstance> cloudstackInstances = cloudstackInstanceDao.readInInstanceNos(instanceNos);
        Map<Long, CloudstackInstance> cloudstackInstanceMap = new LinkedHashMap<Long, CloudstackInstance>();
        for (CloudstackInstance cloudstackInstance : cloudstackInstances) {
            cloudstackInstanceMap.put(cloudstackInstance.getInstanceNo(), cloudstackInstance);
        }

        // CloudStackアドレスを取得
        List<CloudstackAddress> cloudstackAddresses = cloudstackAddressDao.readByAccount(farm.getUserNo());
        Map<Long, CloudstackAddress> cloudstackAddressMap = new LinkedHashMap<Long, CloudstackAddress>();
        for (CloudstackAddress cloudstackAddress : cloudstackAddresses) {
            if (cloudstackAddress.getInstanceNo() != null) {
                cloudstackAddressMap.put(cloudstackAddress.getInstanceNo(), cloudstackAddress);
            }
        }

        // CloudStackボリュームを取得
        List<CloudstackVolume> farmCloudstackVolumes = cloudstackVolumeDao.readByFarmNo(farmNo);
        Map<Long, List<CloudstackVolume>> cloudstackVolumesMap = new LinkedHashMap<Long, List<CloudstackVolume>>();
        for (CloudstackVolume cloudstackVolume : farmCloudstackVolumes) {
            Long instanceNo = cloudstackVolume.getInstanceNo();
            if (instanceNo != null) {
                List<CloudstackVolume> cloudstackVolumes = cloudstackVolumesMap.get(instanceNo);
                if (cloudstackVolumes == null) {
                    cloudstackVolumes = new ArrayList<CloudstackVolume>();
                    cloudstackVolumesMap.put(instanceNo, cloudstackVolumes);
                }
                cloudstackVolumes.add(cloudstackVolume);
            }
        }

        // VMwareインスタンスを取得
        List<VmwareInstance> vmwareInstances = vmwareInstanceDao.readInInstanceNos(instanceNos);
        Map<Long, VmwareInstance> vmwareInstanceMap = new LinkedHashMap<Long, VmwareInstance>();
        for (VmwareInstance vmwareInstance : vmwareInstances) {
            vmwareInstanceMap.put(vmwareInstance.getInstanceNo(), vmwareInstance);
        }

        // VMwareAddressを取得
        Map<Long, VmwareAddress> vmwareAddressMap = new LinkedHashMap<Long, VmwareAddress>();
        List<VmwareAddress> vmwareAddresses = vmwareAddressDao.readByUserNo(farm.getUserNo());
        for (VmwareAddress vmwareAddress : vmwareAddresses) {
            if (vmwareAddress.getInstanceNo() != null) {
                vmwareAddressMap.put(vmwareAddress.getInstanceNo(), vmwareAddress);
            }
        }

        // VMwareキーペアを取得
        Map<Long, VmwareKeyPair> vmwareKeyPairMap = new LinkedHashMap<Long, VmwareKeyPair>();
        if (!vmwareInstanceMap.isEmpty()) {
            List<VmwareKeyPair> vmwareKeyPairs = vmwareKeyPairDao.readByUserNo(farm.getUserNo());
            for (VmwareKeyPair vmwareKeyPair : vmwareKeyPairs) {
                vmwareKeyPairMap.put(vmwareKeyPair.getKeyNo(), vmwareKeyPair);
            }
        }

        // VMwareディスクを取得
        List<VmwareDisk> farmVmwareDisks = vmwareDiskDao.readByFarmNo(farmNo);
        Map<Long, List<VmwareDisk>> vmwareDisksMap = new LinkedHashMap<Long, List<VmwareDisk>>();
        for (VmwareDisk vmwareDisk : farmVmwareDisks) {
            Long instanceNo = vmwareDisk.getInstanceNo();
            if (instanceNo != null) {
                List<VmwareDisk> vmwareDisks = vmwareDisksMap.get(instanceNo);
                if (vmwareDisks == null) {
                    vmwareDisks = new ArrayList<VmwareDisk>();
                    vmwareDisksMap.put(instanceNo, vmwareDisks);
                }
                vmwareDisks.add(vmwareDisk);
            }
        }

        // Niftyインスタンスを取得
        List<NiftyInstance> niftyInstances = niftyInstanceDao.readInInstanceNos(instanceNos);
        Map<Long, NiftyInstance> niftyInstanceMap = new LinkedHashMap<Long, NiftyInstance>();
        for (NiftyInstance niftyInstance : niftyInstances) {
            niftyInstanceMap.put(niftyInstance.getInstanceNo(), niftyInstance);
        }

        // Niftyキーペアを取得
        Map<Long, NiftyKeyPair> niftyKeyPairMap = new LinkedHashMap<Long, NiftyKeyPair>();
        if (!niftyInstanceMap.isEmpty()) {
            List<NiftyKeyPair> niftyKeyPairs = niftyKeyPairDao.readByUserNo(farm.getUserNo());
            for (NiftyKeyPair niftyKeyPair : niftyKeyPairs) {
                niftyKeyPairMap.put(niftyKeyPair.getKeyNo(), niftyKeyPair);
            }
        }

        // AWS認証情報を取得
        List<AwsCertificate> awsCertificates = awsCertificateDao.readByUserNo(farm.getUserNo());
        Map<Long, AwsCertificate> awsCertificateMap = new LinkedHashMap<Long, AwsCertificate>();
        for (AwsCertificate awsCertificate: awsCertificates) {
            awsCertificateMap.put(awsCertificate.getPlatformNo(), awsCertificate);
        }

        // インスタンスに紐づくコンポーネント情報を取得
        List<InstanceDto> dtos = new ArrayList<InstanceDto>();
        for (Instance instance : instances) {
            PlatformDto platformDto = new PlatformDto();
            ImageDto imageDto = new ImageDto();

            Platform platform = platformMap.get(instance.getPlatformNo());
            platformDto.setPlatform(platform);

            Image image = imageMap.get(instance.getImageNo());
            imageDto.setImage(image);
            if ("aws".equals(platform.getPlatformType())) {
                platformDto.setPlatformAws(platformAwsMap.get(instance.getPlatformNo()));
                imageDto.setImageAws(imageAwsMap.get(instance.getImageNo()));
            } else if ("cloudstack".equals(platform.getPlatformType())) {
                platformDto.setPlatformCloudstack(platformCloudstackMap.get(instance.getPlatformNo()));
                imageDto.setImageCloudstack(imageCloudstackMap.get(instance.getImageNo()));
            } else if ("vmware".equals(platform.getPlatformType())) {
                platformDto.setPlatformVmware(platformVmwareMap.get(instance.getPlatformNo()));
                imageDto.setImageVmware(imageVmwareMap.get(instance.getImageNo()));
            } else if ("nifty".equals(platform.getPlatformType())) {
                platformDto.setPlatformNifty(platformNiftyMap.get(instance.getPlatformNo()));
                imageDto.setImageNifty(imageNiftyMap.get(instance.getImageNo()));
            }

            List<InstanceConfig> instanceConfigs = instanceConfigDao.readByInstanceNo(instance.getInstanceNo());

            List<ComponentInstanceDto> componentInstances = new ArrayList<ComponentInstanceDto>();
            for (ComponentInstance componentInstance : componentInstanceMap.get(instance.getInstanceNo())) {
                ComponentInstanceDto componentInstanceDto = new ComponentInstanceDto();
                componentInstanceDto.setComponentInstance(componentInstance);

                Component component = componentMap.get(componentInstance.getComponentNo());
                String url = createUrl(instance.getPublicIp(), component.getComponentTypeNo());
                componentInstanceDto.setUrl(url);
                componentInstances.add(componentInstanceDto);
            }

            // 有効無効に応じてステータスを変更する（画面表示用）
            InstanceStatus instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
            if (BooleanUtils.isTrue(instance.getEnabled())) {
                if (instanceStatus == InstanceStatus.STOPPED) {
                    instance.setStatus(InstanceStatus.STARTING.toString());
                }
            } else {
                if (instanceStatus == InstanceStatus.RUNNING || instanceStatus == InstanceStatus.WARNING) {
                    instance.setStatus(InstanceStatus.STOPPING.toString());
                }
            }

            // 画面表示用にステータスの変更
            //    サーバステータス 協調設定ステータス   変換後サーバステータス
            //        Running         Coodinating            Configuring
            //        Running         Warning                Warning
            instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
            InstanceCoodinateStatus insCoodiStatus = InstanceCoodinateStatus.fromStatus(instance.getCoodinateStatus());
            // サーバステータス(Running)かつ協調設定ステータス(Coodinating)⇒「Configuring」
            if (instanceStatus == InstanceStatus.RUNNING && insCoodiStatus == InstanceCoodinateStatus.COODINATING) {
                instance.setStatus(InstanceStatus.CONFIGURING.toString());
            // サーバステータス(Running)かつ協調設定ステータス(Warning)⇒「Warning」
            } else if (instanceStatus == InstanceStatus.RUNNING && insCoodiStatus == InstanceCoodinateStatus.WARNING) {
                instance.setStatus(InstanceStatus.WARNING.toString());
            }

            // インスタンスごとのコンポーネントのステータスを調整する（画面表示用）
            instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
            for (ComponentInstanceDto componentInstanceDto : componentInstances) {
                ComponentInstance componentInstance = componentInstanceDto.getComponentInstance();
                ComponentInstanceStatus status = ComponentInstanceStatus.fromStatus(componentInstance.getStatus());
                if (BooleanUtils.isTrue(componentInstance.getEnabled())) {
                    if (status == ComponentInstanceStatus.STOPPED) {
                        if (instanceStatus == InstanceStatus.WARNING) {
                            // インスタンスがWaringであれば、コンポーネントもWarningとする
                            componentInstance.setStatus(ComponentInstanceStatus.WARNING.toString());
                        } else if (BooleanUtils.isTrue(farm.getScheduled())) {
                            componentInstance.setStatus(ComponentInstanceStatus.STARTING.toString());
                        }
                    }
                } else {
                    if (status == ComponentInstanceStatus.RUNNING || status == ComponentInstanceStatus.WARNING) {
                        if (BooleanUtils.isTrue(farm.getScheduled())) {
                            // ファームが処理対象であれば、Stoppingにする
                            componentInstance.setStatus(ComponentInstanceStatus.STOPPING.toString());
                        }
                    }
                }
            }

            AwsInstance awsInstance = awsInstanceMap.get(instance.getInstanceNo());
            AwsAddress awsAddress = awsAddressMap.get(instance.getInstanceNo());
            List<AwsVolume> awsVolumes = awsVolumesMap.get(instance.getInstanceNo());

            CloudstackInstance cloudstackInstance = cloudstackInstanceMap.get(instance.getInstanceNo());
            CloudstackAddress cloudstackAddress = cloudstackAddressMap.get(instance.getInstanceNo());
            List<CloudstackVolume> cloudstackVolumes = cloudstackVolumesMap.get(instance.getInstanceNo());

            VmwareInstance vmwareInstance = vmwareInstanceMap.get(instance.getInstanceNo());
            VmwareAddress vmwareAddress = vmwareAddressMap.get(instance.getInstanceNo());
            VmwareKeyPair vmwareKeyPair = null;
            if (vmwareInstance != null) {
                vmwareKeyPair = vmwareKeyPairMap.get(vmwareInstance.getKeyPairNo());
            }
            List<VmwareDisk> vmwareDisks = vmwareDisksMap.get(instance.getInstanceNo());
            NiftyInstance niftyInstance = niftyInstanceMap.get(instance.getInstanceNo());
            NiftyKeyPair niftyKeyPair = null;
            if (niftyInstance != null) {
                niftyKeyPair = niftyKeyPairMap.get(niftyInstance.getKeyPairNo());
            }

            AwsCertificate awsCertificate = awsCertificateMap.get(instance.getPlatformNo());

            // ソート
            Collections.sort(componentInstances, Comparators.COMPARATOR_COMPONENT_INSTANCE_DTO);

            InstanceDto dto = new InstanceDto();
            dto.setInstance(instance);
            dto.setPlatform(platformDto);
            dto.setImage(imageDto);
            dto.setInstanceConfigs(instanceConfigs);
            dto.setComponentInstances(componentInstances);
            dto.setAwsInstance(awsInstance);
            dto.setAwsAddress(awsAddress);
            dto.setAwsVolumes(awsVolumes);
            dto.setAwsCertificate(awsCertificate);
            dto.setCloudstackInstance(cloudstackInstance);
            dto.setCloudstackAddress(cloudstackAddress);
            dto.setCloudstackVolumes(cloudstackVolumes);
            dto.setVmwareInstance(vmwareInstance);
            dto.setVmwareAddress(vmwareAddress);
            dto.setVmwareKeyPair(vmwareKeyPair);
            dto.setVmwareDisks(vmwareDisks);
            dto.setNiftyInstance(niftyInstance);
            dto.setNiftyKeyPair(niftyKeyPair);
            dtos.add(dto);
        }

        // ソート
        Collections.sort(dtos, Comparators.COMPARATOR_INSTANCE_DTO);

        return dtos;
    }

    protected Long createInstance(Long farmNo, String instanceName, Long platformNo, String comment, Long imageNo) {
        // 引数チェック
        if (farmNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "farmNo");
        }
        if (instanceName == null || instanceName.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceName");
        }
        if (platformNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "platformNo");
        }
        if (imageNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "imageNo");
        }

        // 形式チェック
        if (!Pattern.matches("^[0-9a-z]|[0-9a-z][0-9a-z-]*[0-9a-z]$", instanceName)) {
            throw new AutoApplicationException("ECOMMON-000012", "instanceName");
        }

        // イメージ番号のチェック
        Image image = imageDao.read(imageNo);
        Platform platform = platformDao.read(platformNo);
        if (platformNo.equals(image.getPlatformNo()) == false) {
            throw new AutoApplicationException("ESERVICE-000405", imageNo, platform.getPlatformName());
        }

        // TODO: 長さチェック

        // インスタンス名の一意チェック
        Instance checkInstance = instanceDao.readByFarmNoAndInstanceName(farmNo, instanceName);
        if (checkInstance != null) {
            // 同名のインスタンスが存在する場合
            throw new AutoApplicationException("ESERVICE-000401", instanceName);
        }

        // ロードバランサ名のチェック
        LoadBalancer checkLoadBalancer = loadBalancerDao.readByFarmNoAndLoadBalancerName(farmNo, instanceName);
        if (checkLoadBalancer != null) {
            // 同名のロードバランサが存在する場合
            throw new AutoApplicationException("ESERVICE-000417", instanceName);
        }

        // ファームの存在チェック
        Farm farm = farmDao.read(farmNo);
        if (farm == null) {
            throw new AutoApplicationException("ESERVICE-000406", farmNo);
        }

        // fqdnの長さチェック
        String fqdn = instanceName + "." + farm.getDomainName();
        if (fqdn.length() > 63) {
            throw new AutoApplicationException("ESERVICE-000418", fqdn);
        }

        // TODO: 整合性チェック

        // VMwareプラットフォームでのWindowsの場合、VMwareプラットフォーム全体の中に同名のWindowsがいないことのチェック
        // TODO: OS種別の判定方法を見直す
        if ("vmware".equals(platform.getPlatformType()) && StringUtils.startsWithIgnoreCase(image.getOs(), "windows")) {
            List<Instance> allInstances = instanceDao.readAll();
            for (Instance instance2 : allInstances) {
                if (StringUtils.equals(instanceName, instance2.getInstanceName())) {
                    Platform platform2 = platformDao.read(instance2.getPlatformNo());
                    if ("vmware".equals(platform2.getPlatformType())) {
                        Image image2 = imageDao.read(instance2.getImageNo());
                        if (StringUtils.startsWithIgnoreCase(image2.getOs(), "windows")) {
                            // VMwareプラットフォーム上に同名のWindowsがいる場合
                            throw new AutoApplicationException("ESERVICE-000419", instanceName);
                        }
                    }
                }
            }
        }

        // インスタンスコードの作成
        String instanceCode = passwordGenerator.generate(20);

        // インスタンスの作成
        Instance instance = new Instance();
        instance.setFarmNo(farmNo);
        instance.setInstanceName(instanceName);
        instance.setPlatformNo(platformNo);
        instance.setImageNo(imageNo);
        instance.setEnabled(false);
        instance.setComment(comment);
        instance.setFqdn(fqdn);
        instance.setInstanceCode(instanceCode);
        instance.setStatus(InstanceStatus.STOPPED.toString());
        instanceDao.create(instance);

        // TODO: OS種別の判定方法を見直す
        if (!StringUtils.startsWithIgnoreCase(image.getOs(), "windows") ||
           (StringUtils.startsWithIgnoreCase(image.getOs(), "windows") && "vmware".equals(platform.getPlatformType()))) {
            // Puppetインスタンスの作成
            PuppetInstance puppetInstance = new PuppetInstance();
            puppetInstance.setInstanceNo(instance.getInstanceNo());
            puppetInstanceDao.create(puppetInstance);
        }

        Boolean useZabbix = BooleanUtils.toBooleanObject(Config.getProperty("zabbix.useZabbix"));
        if (BooleanUtils.isTrue(useZabbix)) {
            // Zabbixインスタンスの作成
            ZabbixInstance zabbixInstance = new ZabbixInstance();
            zabbixInstance.setInstanceNo(instance.getInstanceNo());
            zabbixInstanceDao.create(zabbixInstance);
        }

        return instance.getInstanceNo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long createIaasInstance(Long farmNo, String instanceName,
                        Long platformNo, String comment, Long imageNo, String instanceType) {
        // インスタンスの作成
        Long instanceNo = createInstance(farmNo, instanceName, platformNo, comment, imageNo);

        // プラットフォームのチェック
        Platform platform = platformDao.read(platformNo);
        if ("aws".equals(platform.getPlatformType()) == false &&
            "cloudstack".equals(platform.getPlatformType()) == false) {
            throw new AutoApplicationException("ESERVICE-000404", instanceName);
        }

        //ファームの取得
        Farm farm = farmDao.read(farmNo);

        if ("aws".equals(platform.getPlatformType())){
            PlatformAws platformAws = platformAwsDao.read(platformNo);
            makeAwsData(farm, instanceNo, instanceType, platformAws);
        } else if ("cloudstack".equals(platform.getPlatformType())){
            PlatformCloudstack platformCloudstack = platformCloudstackDao.read(platformNo);
            makeCloudStackData(farm, instanceNo, instanceType, platformCloudstack);
        }


        // イベントログ出力
        eventLogger.log(EventLogLevel.INFO, farmNo, farm.getFarmName(), null, null, instanceNo, instanceName,
                "InstanceCreate",instanceType, platformNo, new Object[] { platform.getPlatformName() });

        return instanceNo;
    }

    private void makeAwsData(Farm farm, Long instanceNo, String instanceType, PlatformAws platformAws) {

        // 引数チェック
        try {
            InstanceType.fromValue(instanceType);
        } catch (IllegalArgumentException e) {
            throw new AutoApplicationException("ECOMMON-000001", "instanceType");
        }

        // AWSインスタンスの作成
        AwsInstance awsInstance = new AwsInstance();
        awsInstance.setInstanceNo(instanceNo);
        awsInstance.setInstanceType(instanceType);

        //KeyName
        AwsCertificate awsCertificate = awsCertificateDao.read(farm.getUserNo(), platformAws.getPlatformNo());
        //キーペアの取得
        List<KeyPairDto> keyPairInfos = iaasDescribeService.getKeyPairs(farm.getUserNo(), platformAws.getPlatformNo());
        String keyName = null;
        // AWS認証情報に設定されているデフォルトキーペアを設定
        for (KeyPairDto keyPairDto: keyPairInfos) {
            if (StringUtils.equals(awsCertificate.getDefKeypair(), keyPairDto.getKeyName())) {
                keyName = keyPairDto.getKeyName();
                break;
            }
        }
        if (keyName == null && keyPairInfos.size() > 0){
            //デフォルトキーペアが存在しない場合は1件目
            keyName = keyPairInfos.get(0).getKeyName();
        }
        awsInstance.setKeyName(keyName);

        String subnetId = null;
        if (platformAws.getEuca() == false && platformAws.getVpc()) {
            // VPCの場合
            // SubnetId & AvailabilityZone
            List<SubnetDto> subnets = iaasDescribeService.getSubnets(farm.getUserNo(), platformAws.getPlatformNo(), platformAws.getVpcId());
            SubnetDto subnet = null;
            for (SubnetDto subnetDto: subnets) {
                //デフォルトサブネットを設定
                if (StringUtils.equals(awsCertificate.getDefSubnet(), subnetDto.getSubnetId()) &&
                    StringUtils.equals(platformAws.getAvailabilityZone(), subnetDto.getZoneid())) {
                    //サブネットとゾーンが一致するものを設定
                    subnet = subnetDto;
                    break;
                }
            }
            if (subnet != null) {
                subnetId = subnet.getSubnetId();
                awsInstance.setSubnetId(subnetId);
                awsInstance.setAvailabilityZone(subnet.getZoneid());
            }
        } else {
            // VPCでない場合
            // AvailabilityZone
            String zoneName = platformAws.getAvailabilityZone();
            if (StringUtils.isEmpty(zoneName) && platformAws.getEuca()) {
                // デフォルトのゾーン名が指定されておらず、Eucalyptusの場合のみAPIでゾーン名を取得する
                List<ZoneDto> availabilityZones =
                        iaasDescribeService.getAvailabilityZones(farm.getUserNo(), platformAws.getPlatformNo());

                zoneName = availabilityZones.get(0).getZoneName();
            }
            awsInstance.setAvailabilityZone(zoneName);
        }

        // SecurityGroup
        String groupName = null;
        List<SecurityGroupDto> securityGroups = null;
        if (platformAws.getEuca() == false && platformAws.getVpc()) {
            // VPCの場合
            securityGroups = iaasDescribeService.getSecurityGroups(farm.getUserNo(), platformAws.getPlatformNo(), platformAws.getVpcId());
        } else {
            // VPCでない場合
            securityGroups = iaasDescribeService.getSecurityGroups(farm.getUserNo(), platformAws.getPlatformNo(), null);
        }
        // 「default」のセキュリティグループがあれば「default」を設定
        for (SecurityGroupDto securityGroup : securityGroups) {
            if ("default".equals(securityGroup.getGroupName())) {
                groupName = securityGroup.getGroupName();
                break;
            }
        }
        // 「default」がなければ1件目
        if (groupName == null && securityGroups.size() > 0) {
            groupName = securityGroups.get(0).getGroupName();
        }
        awsInstance.setSecurityGroups(groupName);

        awsInstanceDao.create(awsInstance);
    }

    private void makeCloudStackData(Farm farm, Long instanceNo, String instanceType, PlatformCloudstack platformCloudstack) {

        // AWSインスタンスの作成
        CloudstackInstance cloudstackInstance = new CloudstackInstance();
        cloudstackInstance.setInstanceNo(instanceNo);
        cloudstackInstance.setInstanceType(instanceType);

        //KeyName
        //Cloudstack認証情報の取得
        CloudstackCertificate cloudstackCertificate = cloudstackCertificateDao.read(farm.getUserNo(), platformCloudstack.getPlatformNo());
        //キーペアの取得
        List<KeyPairDto> keyPairInfos = iaasDescribeService.getKeyPairs(farm.getUserNo(), platformCloudstack.getPlatformNo());
        String keyName = null;
        // CLOUDSTACK認証情報に設定されているデフォルトキーペアを設定
        for (KeyPairDto keyPairDto: keyPairInfos) {
            if (StringUtils.equals(cloudstackCertificate.getDefKeypair(), keyPairDto.getKeyName())) {
                keyName = keyPairDto.getKeyName();
                break;
            }
        }
        if (keyName == null && keyPairInfos.size() > 0){
            //デフォルトキーペアが存在しない場合は1件目
            keyName = keyPairInfos.get(0).getKeyName();
        }
        cloudstackInstance.setKeyName(keyName);

        //TODO ZONEID, NETWORKID, SECURITYGROUP はどうやって手に入れるか　@@今はプロパティからを想定しておく
        String zoneId = platformCloudstack.getZoneId();
        String[] networks = platformCloudstack.getNetworkId().split(",");
        String netId = networks[0];

        cloudstackInstance.setZoneid(zoneId);
        cloudstackInstance.setNetworkid(netId);

        String groupName = null;
        List<SecurityGroupDto> securityGroups = iaasDescribeService.getSecurityGroups(farm.getUserNo(), platformCloudstack.getPlatformNo(), null);
        for (SecurityGroupDto securityGroup : securityGroups) {
            if ("default".equals(securityGroup.getGroupName())) {
                groupName = "default";
                break;
            }
        }
        if (groupName == null) {
            groupName = securityGroups.get(0).getGroupName();
        }
        cloudstackInstance.setSecuritygroup(groupName);

        cloudstackInstanceDao.create(cloudstackInstance);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Long createVmwareInstance(Long farmNo, String instanceName, Long platformNo, String comment, Long imageNo,
            String instanceType) {
        // インスタンスの作成
        Long instanceNo = createInstance(farmNo, instanceName, platformNo, comment, imageNo);

        // 引数チェック
        if (instanceType == null || instanceType.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceType");
        }

        // プラットフォームのチェック
        Platform platform = platformDao.read(platformNo);
        if ("vmware".equals(platform.getPlatformType()) == false) {
            throw new AutoApplicationException("ESERVICE-000408", instanceName);
        }
        PlatformVmware platformVmware = platformVmwareDao.read(platformNo);

        // VMwareインスタンスの作成
        VmwareInstance vmwareInstance = new VmwareInstance();
        vmwareInstance.setInstanceNo(instanceNo);

        // 仮想マシン名
        Farm farm = farmDao.read(farmNo);
        String machineName = farm.getFarmName() + "_" + instanceName;
        vmwareInstance.setMachineName(machineName);

        // InstanceType
        vmwareInstance.setInstanceType(instanceType);

        // クラスタ、ホスト
        String computeResource = platformVmware.getComputeResource();
        if (computeResource == null) {
            List<ComputeResource> computeResources = vmwareDescribeService.getComputeResources(platformNo);
            computeResource = computeResources.get(0).getName();
        }
        vmwareInstance.setComputeResource(computeResource);

        // リソースプール
        // TODO: リソースプールの選択をどうする？
        String resourcePool = null;
        vmwareInstance.setResourcePool(resourcePool);

        // データストア（この時点ではデータストアを決めない）
        String datastore = null;
        vmwareInstance.setDatastore(datastore);

        // キーペア
        List<VmwareKeyPair> vmwareKeyPairs = vmwareKeyPairDao.readByUserNoAndPlatformNo(farm.getUserNo(), platformNo);
        Long keyPairNo = vmwareKeyPairs.get(0).getKeyNo();
        vmwareInstance.setKeyPairNo(keyPairNo);

        vmwareInstanceDao.create(vmwareInstance);

        // イベントログ出力
        eventLogger.log(EventLogLevel.INFO, farmNo, farm.getFarmName(), null, null, instanceNo, instanceName,
                "InstanceCreate",instanceType, platformNo, new Object[] { platform.getPlatformName() });

        return instanceNo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long createNiftyInstance(Long farmNo, String instanceName, Long platformNo, String comment, Long imageNo,
            String instanceType) {
        // インスタンスの作成
        Long instanceNo = createInstance(farmNo, instanceName, platformNo, comment, imageNo);

        // 引数チェック
        if (instanceType == null || instanceType.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceType");
        }

        // プラットフォームのチェック
        Platform platform = platformDao.read(platformNo);
        if ("nifty".equals(platform.getPlatformType()) == false) {
            throw new AutoApplicationException("ESERVICE-000410", instanceName);
        }

        // Niftyインスタンスの作成
        NiftyInstance niftyInstance = new NiftyInstance();
        niftyInstance.setInstanceNo(instanceNo);
        niftyInstance.setInstanceType(instanceType);

        Farm farm = farmDao.read(farmNo);
        List<NiftyKeyPair> niftyKeyPairs = niftyKeyPairDao.readByUserNoAndPlatformNo(farm.getUserNo(), platformNo);
        niftyInstance.setKeyPairNo(niftyKeyPairs.get(0).getKeyNo());

        niftyInstanceDao.create(niftyInstance);

        // イベントログ出力
        eventLogger.log(EventLogLevel.INFO, farmNo, farm.getFarmName(), null, null, instanceNo, instanceName,
                "InstanceCreate",instanceType, platformNo, new Object[] { platform.getPlatformName() });

        return instanceNo;
    }

    protected void updateInstance(Long instanceNo, String instanceName, String comment) {
        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }
        if (instanceName == null || instanceName.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceName");
        }

        // 形式チェック
        if (!Pattern.matches("^[0-9a-z]|[0-9a-z][0-9a-z-]*[0-9a-z]$", instanceName)) {
            throw new AutoApplicationException("ECOMMON-000012", "instanceName");
        }

        // TODO: 長さチェック

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            // インスタンスが存在しない場合
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // インスタンスが停止状態でない場合
        if (InstanceStatus.fromStatus(instance.getStatus()) != InstanceStatus.STOPPED) {
            // 停止状態でないと変更できないものを変更していないかチェック
            if (!StringUtils.equals(instance.getInstanceName(), instanceName)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
        }

        // インスタンス名を変更する場合
        if (!StringUtils.equals(instance.getInstanceName(), instanceName)) {
            // インスタンス名の一意チェック
            Instance checkInstance = instanceDao.readByFarmNoAndInstanceName(instance.getFarmNo(), instanceName);
            if (checkInstance != null && !instanceNo.equals(checkInstance.getInstanceNo())) {
                // 同名のインスタンスが存在する場合
                throw new AutoApplicationException("ESERVICE-000401", instanceName);
            }

            // ロードバランサ名のチェック
            LoadBalancer checkLoadBalancer = loadBalancerDao.readByFarmNoAndLoadBalancerName(instance.getFarmNo(),
                    instanceName);
            if (checkLoadBalancer != null) {
                // 同名のロードバランサが存在する場合
                throw new AutoApplicationException("ESERVICE-000417", instanceName);
            }
        }

        // インスタンスの更新
        Farm farm = farmDao.read(instance.getFarmNo());

        instance.setInstanceName(instanceName);
        instance.setComment(comment);
        instance.setFqdn(instanceName + "." + farm.getDomainName());
        instanceDao.update(instance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateAwsInstance(Long instanceNo, String instanceName, String comment, String keyName,
            String instanceType, String securityGroupName, String availabilityZoneName, Long addressNo, String subnetId, String privateIpAddress) {
        // インスタンスの更新
        updateInstance(instanceNo, instanceName, comment);

        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }
        if (keyName == null || keyName.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "keyName");
        }
        try {
            InstanceType.fromValue(instanceType);
        } catch (IllegalArgumentException e) {
            throw new AutoApplicationException("ECOMMON-000001", "instanceType");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // プラットフォームのチェック
        Platform platform = platformDao.read(instance.getPlatformNo());
        if ("aws".equals(platform.getPlatformType()) == false) {
            throw new AutoApplicationException("ESERVICE-000404", instance.getInstanceName());
        }

        //ファーム取得
        Farm farm = farmDao.read(instance.getFarmNo());

        // インスタンスが停止状態でない場合
        AwsInstance awsInstance = awsInstanceDao.read(instanceNo);
        if (InstanceStatus.fromStatus(instance.getStatus()) != InstanceStatus.STOPPED) {
            // 停止状態でないと変更できないものを変更していないかチェック
            if (!StringUtils.equals(awsInstance.getKeyName(), keyName)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (!StringUtils.equals(awsInstance.getInstanceType(), instanceType)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (StringUtils.isEmpty(awsInstance.getSecurityGroups()) ? StringUtils.isNotEmpty(securityGroupName)
                    : !StringUtils.equals(awsInstance.getSecurityGroups(), securityGroupName)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (StringUtils.isEmpty(awsInstance.getAvailabilityZone()) ? StringUtils.isNotEmpty(availabilityZoneName)
                    : !StringUtils.equals(awsInstance.getAvailabilityZone(), availabilityZoneName)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (StringUtils.isEmpty(awsInstance.getSubnetId()) ? StringUtils.isNotEmpty(subnetId)
                    : !StringUtils.equals(awsInstance.getSubnetId(), subnetId)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (StringUtils.isEmpty(awsInstance.getPrivateIpAddress()) ? StringUtils.isNotEmpty(privateIpAddress)
                    : !StringUtils.equals(awsInstance.getPrivateIpAddress(), privateIpAddress)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
        }

        // セキュリティグループのチェック
        if (StringUtils.isEmpty(awsInstance.getSubnetId()) && StringUtils.isEmpty(securityGroupName)) {
            throw new AutoApplicationException("ECOMMON-000003", "securityGroupName");
        }

        // ゾーンのチェック
        PlatformAws platformAws = platformAwsDao.read(instance.getPlatformNo());
        if (platformAws.getEuca() == false && platformAws.getVpc()) {
            if (StringUtils.equals(awsInstance.getSubnetId(), subnetId) == false) {
                //VPCの場合、サブネットでゾーンが決まるのでサブネットで判断する
                if (awsVolumeDao.countByInstanceNo(instanceNo) > 0) {
                    // EBS作成後はサブネット(ゾーン)を変更できない
                    throw new AutoApplicationException("ESERVICE-000421");
                }
            }
        } else {
            if (StringUtils.isEmpty(awsInstance.getAvailabilityZone()) ? StringUtils.isNotEmpty(availabilityZoneName)
                    : !StringUtils.equals(awsInstance.getAvailabilityZone(), availabilityZoneName)) {
                if (awsVolumeDao.countByInstanceNo(instanceNo) > 0) {
                    // EBS作成後はゾーンを変更できない
                    throw new AutoApplicationException("ESERVICE-000412");
                }
            }
        }

        //サブネットのチェック
        if (StringUtils.isNotEmpty(subnetId) && StringUtils.isNotEmpty(privateIpAddress)) {
            //AWS_INSTANCEのsubnet重複チェック
            List<Instance> instances = instanceDao.readByFarmNo(farm.getFarmNo());
            List<Long> instanceNos = new ArrayList<Long>();
            for (Instance tmpInstance: instances) {
                if (instanceNo.equals(tmpInstance.getInstanceNo()) == false) {
                    instanceNos.add(tmpInstance.getInstanceNo());
                }
            }
            List<AwsInstance> awsInstances = awsInstanceDao.readInInstanceNos(instanceNos);
            for (AwsInstance tmpAwsInstance: awsInstances) {
                if (subnetId.equals(tmpAwsInstance.getSubnetId()) &&
                    privateIpAddress.equals(tmpAwsInstance.getPrivateIpAddress())) {
                    //同じsubnetIdで同じprivateIpAddressの別のAWS_INSTANCEが存在する場合
                    throw new AutoApplicationException("ESERVICE-000420", privateIpAddress);
                }
            }
        }

        // AWSアドレスのチェック
        AwsAddress awsAddress = null;
        if (addressNo != null) {
            // AWSアドレスの存在チェック
            awsAddress = awsAddressDao.read(addressNo);
            if (awsAddress == null) {
                throw new AutoApplicationException("ESERVICE-000415", addressNo);
            }

            // 他インスタンスに割り当てられていないかどうかのチェック
            if (awsAddress.getInstanceNo() != null && !instanceNo.equals(awsAddress.getInstanceNo())) {
                // 他のインスタンスに割り当てられている場合
                throw new AutoApplicationException("ESERVICE-000416", awsAddress.getPublicIp());
            }
        }

        // AWSインスタンスの更新
        awsInstance.setKeyName(keyName);
        awsInstance.setInstanceType(instanceType);
        awsInstance.setSecurityGroups(securityGroupName);
        awsInstance.setAvailabilityZone(availabilityZoneName);
        awsInstance.setSubnetId(subnetId);
        awsInstance.setPrivateIpAddress(privateIpAddress);
        awsInstanceDao.update(awsInstance);

        // 他のAWSアドレスが割り当てられている場合、割り当てを外す
        List<AwsAddress> awsAddresses = awsAddressDao.readByInstanceNo(instanceNo);
        for (AwsAddress address : awsAddresses) {
            if (address.getAddressNo().equals(addressNo)) {
                continue;
            }
            address.setInstanceNo(null);
            awsAddressDao.update(address);
        }

        // AWSアドレスをインスタンスに割り当てる
        if (addressNo != null && !instanceNo.equals(awsAddress.getInstanceNo())) {
            awsAddress.setInstanceNo(instanceNo);
            awsAddressDao.update(awsAddress);
        }

        // イベントログ出力
        eventLogger.log(EventLogLevel.INFO, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo, instanceName,
                "InstanceUpdate",instanceType, instance.getPlatformNo(), null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void updateCloudstackInstance(Long instanceNo, String instanceName, String comment, String keyName,
            String instanceType, String securityGroupName, String zoneid, Long addressNo){
        // インスタンスの更新
        updateInstance(instanceNo, instanceName, comment);

        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // プラットフォームのチェック
        Platform platform = platformDao.read(instance.getPlatformNo());
        if ("cloudstack".equals(platform.getPlatformType()) == false) {
            throw new AutoApplicationException("ESERVICE-000404", instance.getInstanceName());
        }

        // インスタンスが停止状態でない場合
        CloudstackInstance cloudstackInstance = cloudstackInstanceDao.read(instanceNo);
        if (InstanceStatus.fromStatus(instance.getStatus()) != InstanceStatus.STOPPED) {
            // 停止状態でないと変更できないものを変更していないかチェック
            if (!StringUtils.equals(cloudstackInstance.getKeyName(), keyName)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (!StringUtils.equals(cloudstackInstance.getInstanceType(), instanceType)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (StringUtils.isEmpty(cloudstackInstance.getSecuritygroup()) ? StringUtils.isNotEmpty(securityGroupName)
                    : !StringUtils.equals(cloudstackInstance.getSecuritygroup(), securityGroupName)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (StringUtils.isEmpty(cloudstackInstance.getZoneid()) ? StringUtils.isNotEmpty(zoneid)
                    : !StringUtils.equals(cloudstackInstance.getZoneid(), zoneid)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
        }

        // ゾーンのチェック
        if (StringUtils.isEmpty(cloudstackInstance.getZoneid()) ? StringUtils.isNotEmpty(zoneid)
                : !StringUtils.equals(cloudstackInstance.getZoneid(), zoneid)) {
            if (cloudstackVolumeDao.countByInstanceNo(instanceNo) > 0) {
                // EBS作成後はゾーンを変更できない
                throw new AutoApplicationException("ESERVICE-000412", instance.getInstanceName());
            }
        }

        //アドレスのチェック
        CloudstackAddress cloudstackAddress = null;
        if (addressNo != null) {
            // アドレスの存在チェック
            cloudstackAddress = cloudstackAddressDao.read(addressNo);
            if (cloudstackAddress == null) {
                throw new AutoApplicationException("ESERVICE-000415", addressNo);
            }

            // 他インスタンスに割り当てられていないかどうかのチェック
            if (cloudstackAddress.getInstanceNo() != null && !instanceNo.equals(cloudstackAddress.getInstanceNo())) {
                // 他のインスタンスに割り当てられている場合
                throw new AutoApplicationException("ESERVICE-000416", cloudstackAddress.getIpaddress());
            }
        }

        // インスタンスの更新
        cloudstackInstance.setKeyName(keyName);
        cloudstackInstance.setInstanceType(instanceType);
        cloudstackInstance.setSecuritygroup(securityGroupName);
        cloudstackInstance.setZoneid(zoneid);
        cloudstackInstanceDao.update(cloudstackInstance);

        // 他のアドレスが割り当てられている場合、割り当てを外す
        List<CloudstackAddress> cloudstackAddresses = cloudstackAddressDao.readByInstanceNo(instanceNo);
        for (CloudstackAddress address : cloudstackAddresses) {
            if (address.getAddressNo().equals(addressNo)) {
                continue;
            }
            address.setInstanceNo(null);
            cloudstackAddressDao.update(address);
        }

        // アドレスをインスタンスに割り当てる
        if (addressNo != null && !instanceNo.equals(cloudstackAddress.getInstanceNo())) {
            cloudstackAddress.setInstanceNo(instanceNo);
            cloudstackAddressDao.update(cloudstackAddress);
        }

        // イベントログ出力
        Farm farm = farmDao.read(instance.getFarmNo());
        eventLogger.log(EventLogLevel.INFO, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo, instanceName,
                "InstanceUpdate",instanceType, instance.getPlatformNo(), null);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void updateVmwareInstance(Long instanceNo, String instanceName, String comment, String instanceType,
            String computeResource, String resourcePool, Long keyPairNo) {
        updateVmwareInstance(instanceNo, instanceName, comment, instanceType, computeResource, resourcePool, keyPairNo,
                null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateVmwareInstance(Long instanceNo, String instanceName, String comment, String instanceType,
            String computeResource, String resourcePool, Long keyPairNo, VmwareAddressDto vmwareAddressDto) {
        // インスタンスの更新
        updateInstance(instanceNo, instanceName, comment);

        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }
        if (instanceType == null || instanceType.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceType");
        }
        if (computeResource == null || computeResource.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "computeResource");
        }
        if (keyPairNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "keyPairNo");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // プラットフォームのチェック
        Platform platform = platformDao.read(instance.getPlatformNo());
        if ("vmware".equals(platform.getPlatformType()) == false) {
            throw new AutoApplicationException("ESERVICE-000408", instance.getInstanceName());
        }

        // インスタンスが停止状態でない場合
        VmwareInstance vmwareInstance = vmwareInstanceDao.read(instanceNo);
        VmwareAddress vmwareAddress = vmwareAddressDao.readByInstanceNo(instanceNo);
        if (InstanceStatus.fromStatus(instance.getStatus()) != InstanceStatus.STOPPED) {
            // 停止状態でないと変更できないものを変更していないかチェック
            if (!StringUtils.equals(vmwareInstance.getInstanceType(), instanceType)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (!StringUtils.equals(vmwareInstance.getComputeResource(), computeResource)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (StringUtils.isEmpty(vmwareInstance.getResourcePool()) ? StringUtils.isNotEmpty(resourcePool)
                    : !StringUtils.equals(vmwareInstance.getResourcePool(), resourcePool)) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (!keyPairNo.equals(vmwareInstance.getKeyPairNo())) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
            if (vmwareAddress == null || BooleanUtils.isNotTrue(vmwareAddress.getEnabled())) {
                if (vmwareAddressDto != null) {
                    throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
                }
            } else {
                if (vmwareAddressDto == null) {
                    throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
                } else if (!StringUtils.equals(vmwareAddress.getIpAddress(), vmwareAddressDto.getIpAddress())
                        || !StringUtils.equals(vmwareAddress.getSubnetMask(), vmwareAddressDto.getSubnetMask())
                        || !StringUtils.equals(vmwareAddress.getDefaultGateway(), vmwareAddressDto.getDefaultGateway())) {
                    throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
                }
            }
        }

        // VMwareインスタンスの更新
        vmwareInstance.setInstanceType(instanceType);
        vmwareInstance.setComputeResource(computeResource);
        vmwareInstance.setResourcePool(resourcePool);
        vmwareInstance.setKeyPairNo(keyPairNo);
        vmwareInstanceDao.update(vmwareInstance);

        Farm farm = farmDao.read(instance.getFarmNo());

        // VmwareAddressの更新
        if (vmwareAddress == null) {
            if (vmwareAddressDto != null) {
                vmwareAddress = new VmwareAddress();
                vmwareAddress.setPlatformNo(instance.getPlatformNo());
                vmwareAddress.setIpAddress(vmwareAddressDto.getIpAddress());
                vmwareAddress.setSubnetMask(vmwareAddressDto.getSubnetMask());
                vmwareAddress.setDefaultGateway(vmwareAddressDto.getDefaultGateway());
                vmwareAddress.setUserNo(farm.getUserNo());
                vmwareAddress.setInstanceNo(instanceNo);
                vmwareAddress.setEnabled(true);
                vmwareAddress.setAssociated(false);
                vmwareAddressDao.create(vmwareAddress);
            }
        } else {
            if (vmwareAddressDto == null) {
                vmwareAddress.setEnabled(false);
            } else {
                boolean change = false;
                if (!StringUtils.equals(vmwareAddress.getIpAddress(), vmwareAddressDto.getIpAddress())
                        || !StringUtils.equals(vmwareAddress.getSubnetMask(), vmwareAddressDto.getSubnetMask())
                        || !StringUtils.equals(vmwareAddress.getDefaultGateway(), vmwareAddressDto.getDefaultGateway())) {
                    change = true;
                }

                vmwareAddress.setIpAddress(vmwareAddressDto.getIpAddress());
                vmwareAddress.setSubnetMask(vmwareAddressDto.getSubnetMask());
                vmwareAddress.setDefaultGateway(vmwareAddressDto.getDefaultGateway());
                vmwareAddress.setEnabled(true);
                if (change) {
                    vmwareAddress.setAssociated(false);
                }
            }
            vmwareAddressDao.update(vmwareAddress);
        }

        // イベントログ出力
        eventLogger.log(EventLogLevel.INFO, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo, instanceName,
                "InstanceUpdate", instanceType, instance.getPlatformNo(), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateNiftyInstance(Long instanceNo, String instanceName, String comment, String instanceType,
            Long keyPairNo) {
        // インスタンスの更新
        updateInstance(instanceNo, instanceName, comment);

        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }
        if (instanceType == null || instanceType.length() == 0) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceType");
        }
        if (keyPairNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "keyPairNo");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // プラットフォームのチェック
        Platform platform = platformDao.read(instance.getPlatformNo());
        if ("nifty".equals(platform.getPlatformType()) == false) {
            throw new AutoApplicationException("ESERVICE-000410", instance.getInstanceName());
        }

        // インスタンスが停止状態でない場合
        NiftyInstance niftyInstance = niftyInstanceDao.read(instanceNo);
        if (InstanceStatus.fromStatus(instance.getStatus()) != InstanceStatus.STOPPED) {
            // 停止状態でないと変更できないものを変更していないかチェック
            if (!StringUtils.equals(niftyInstance.getInstanceType(), instanceType)
                    || !keyPairNo.equals(niftyInstance.getKeyPairNo())) {
                throw new AutoApplicationException("ESERVICE-000407", instance.getInstanceName());
            }
        }

        // インスタンスが作成前であることのチェック
        if (StringUtils.isNotEmpty(niftyInstance.getInstanceId())) {
            // インスタンスが作成前でない場合
            if (!niftyInstance.getKeyPairNo().equals(keyPairNo)) {
                // キーペアを変更しようとした場合
                throw new AutoApplicationException("ESERVICE-000411", instance.getInstanceName());
            }
        }

        // Niftyインスタンスの更新
        niftyInstance.setInstanceType(instanceType);
        niftyInstance.setKeyPairNo(keyPairNo);
        niftyInstanceDao.update(niftyInstance);

        // イベントログ出力
        Farm farm = farmDao.read(instance.getFarmNo());
        eventLogger.log(EventLogLevel.INFO, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo, instanceName,
                "InstanceUpdate", instanceType, instance.getPlatformNo(), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteInstance(Long instanceNo) {
        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            // インスタンスが存在しない場合
            return;
        }

        // インスタンスが停止しているかどうかのチェック
        if (InstanceStatus.fromStatus(instance.getStatus()) != InstanceStatus.STOPPED) {
            // インスタンスが停止状態でない場合
            throw new AutoApplicationException("ESERVICE-000402", instance.getInstanceName());
        }

        // MySQLのMasterでないことのチェック
        List<InstanceConfig> instanceConfigs = instanceConfigDao.readByInstanceNo(instanceNo);
        for (InstanceConfig instanceConfig : instanceConfigs) {
            if (MySQLConstants.CONFIG_NAME_MASTER_INSTANCE_NO.equals(instanceConfig.getConfigName())) {
                if (StringUtils.isEmpty(instanceConfig.getConfigValue())) {
                    // MySQLのMasterの場合
                    throw new AutoApplicationException("ESERVICE-000413", instance.getInstanceName());
                }
            }
        }

        // Zabbixインスタンスの削除処理
        Farm farm = farmDao.read(instance.getFarmNo());
        ZabbixInstance zabbixInstance = zabbixInstanceDao.read(instanceNo);
        if (zabbixInstance != null) {
            if (StringUtils.isNotEmpty(zabbixInstance.getHostid())) {
                try {
                    // Zabbixに登録済みの場合、登録を解除する
                    ZabbixProcessClient client = zabbixProcessClientFactory.createZabbixProcessClient();
                    client.deleteHost(zabbixInstance.getHostid());

                    //イベントログ出力
                    eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo,
                            instance.getInstanceName(), "ZabbixUnregist", null, instance.getPlatformNo(),
                            new Object[] { instance.getFqdn(), zabbixInstance.getHostid() });

                } catch (RuntimeException ignore) {
                    // 登録解除に失敗した場合、警告ログを出してエラーを握りつぶす
                    log.warn(ignore.getMessage());
                }
            }
            zabbixInstanceDao.delete(zabbixInstance);
        }

        // ZabbixDataの削除処理
        ZabbixData zabbixData = zabbixDataDao.read(instanceNo);
        if (zabbixData != null) {
            zabbixDataDao.delete(zabbixData);
        }

        // Puppetインスタンスの削除処理
        puppetInstanceDao.deleteByInstanceNo(instanceNo);

        // Puppetレポートの削除処理
        // TODO: OSのファイルシステムに権限がないため削除できない
        //reportLoader.deleteReportFiles(instance.getFqdn());

        Platform platform = platformDao.read(instance.getPlatformNo());

        if ("aws".equals(platform.getPlatformType())) {
            // AWS関連
            deleteAwsInstance(instanceNo);
        }else if ("cloudstack".equals(platform.getPlatformType())) {
            // Cloudstack関連
            deleteCloudstackInstance(instanceNo);
        } else if ("vmware".equals(platform.getPlatformType())) {
            // VMware関連
            deleteVmwareInstance(instanceNo);
        } else if ("nifty".equals(platform.getPlatformType())) {
            // Nifty関連
            deleteNiftyInstance(instanceNo);
        }

        // コンポーネントとインスタンスの関連の削除処理
        componentInstanceDao.deleteByInstanceNo(instanceNo);

        // ロードバランサとインスタンスの関連の削除処理
        loadBalancerInstanceDao.deleteByInstanceNo(instanceNo);

        // インスタンス設定の削除処理
        instanceConfigDao.deleteByInstanceNo(instanceNo);

        // インスタンスの削除処理
        instanceDao.delete(instance);

        // イベントログ出力
        eventLogger.log(EventLogLevel.INFO, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo,
                instance.getInstanceName(), "InstanceDelete", null, instance.getPlatformNo(), null);
    }

    protected void deleteCloudstackInstance(Long instanceNo) {
        // アドレスの関連を解除
        List<CloudstackAddress> cloudstackAddresses = cloudstackAddressDao.readByInstanceNo(instanceNo);
        for (CloudstackAddress cloudstackAddress : cloudstackAddresses) {
            cloudstackAddress.setInstanceNo(null);
            cloudstackAddressDao.update(cloudstackAddress);
        }

        // ボリュームの削除
        // TODO: ボリューム自体の削除処理を別で行うようにする
        List<CloudstackVolume> cloudstackVolumes = cloudstackVolumeDao.readByInstanceNo(instanceNo);
        Instance instance = instanceDao.read(instanceNo);
        Farm farm = farmDao.read(instance.getFarmNo());
        CloudstackInstance cloudstackInstance = cloudstackInstanceDao.read(instanceNo);
        for (CloudstackVolume cloudstackVolume : cloudstackVolumes) {
            if (StringUtils.isEmpty(cloudstackVolume.getVolumeId())) {
                continue;
            }
            IaasGatewayWrapper gateway = iaasGatewayFactory.createIaasGateway(farm.getUserNo(), cloudstackVolume.getPlatformNo());

            //イベントログ出力
            Platform platform = platformDao.read(gateway.getPlatformNo());
            Component component = componentDao.read(cloudstackVolume.getComponentNo());
            eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), cloudstackVolume.getComponentNo(),
                    //component.getComponentName(), instanceNo, instance.getInstanceName(), "AwsEbsDelete", new Object[] {
                    component.getComponentName(), instanceNo, instance.getInstanceName(), "CloudStackVolumeDelete",
                    cloudstackInstance.getInstanceType(), instance.getPlatformNo(), new Object[] {platform.getPlatformName(), cloudstackVolume.getVolumeId() });

            try {
                // ボリュームの削除
                gateway.deleteVolume(cloudstackVolume.getVolumeId());
                //awsProcessClient.waitDeleteVolume(volumeId); // TODO: EC2ではDeleteVolumeに時間がかかるため、Waitしない

                //イベントログ出力
                eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), cloudstackVolume.getComponentNo(),
                        //component.getComponentName(), instanceNo, instance.getInstanceName(), "AwsEbsDeleteFinish",
                        component.getComponentName(), instanceNo, instance.getInstanceName(), "CloudStackVolumeDeleteFinish",
                        cloudstackInstance.getInstanceType(), instance.getPlatformNo(), new Object[] { platform.getPlatformName(), cloudstackVolume.getVolumeId() });

            } catch (AutoException ignore) {
                // ボリュームが存在しない場合などに備えて例外を握りつぶす
            }
        }
        cloudstackVolumeDao.deleteByInstanceNo(instanceNo);

        // インスタンスの削除処理
        if (StringUtils.isNotEmpty(cloudstackInstance.getInstanceId())) {
            // インスタンス自体の削除処理を別で行うようにする
            IaasGatewayWrapper gateway = iaasGatewayFactory.createIaasGateway(farm.getUserNo(), instance.getPlatformNo());

            // イベントログ出力
            Platform platform = platformDao.read(gateway.getPlatformNo());
            eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo, instance
                    //.getInstanceName(), "AwsInstanceDelete", new Object[] { platform.getPlatformName(),
                    .getInstanceName(), "CloudStackInstanceDelete", cloudstackInstance.getInstanceType(),
                    instance.getPlatformNo(), new Object[] { platform.getPlatformName(), cloudstackInstance.getInstanceId() });

            try {
                // インスタンスの削除
                gateway.terminateInstance(cloudstackInstance.getInstanceId());

            } catch (AutoException ignore) {
                // インスタンスが存在しない場合などに備えて例外を握りつぶす
            }
            // イベントログ出力
            eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo,
                            //instance.getInstanceName(), "AwsInstanceDeleteFinish", new Object[] { platform.getPlatformName(),
                            instance.getInstanceName(), "CloudStackInstanceDeleteFinish", cloudstackInstance.getInstanceType(),
                            instance.getPlatformNo(), new Object[] { platform.getPlatformName(), cloudstackInstance.getInstanceId() });
        }
        cloudstackInstanceDao.deleteByInstanceNo(instanceNo);
    }

    protected void deleteAwsInstance(Long instanceNo) {
        // アドレスの関連を解除
        List<AwsAddress> awsAddresses = awsAddressDao.readByInstanceNo(instanceNo);
        for (AwsAddress awsAddress : awsAddresses) {
            awsAddress.setInstanceNo(null);
            awsAddressDao.update(awsAddress);
        }

        // AWSボリュームの削除
        // TODO: ボリューム自体の削除処理を別で行うようにする
        List<AwsVolume> awsVolumes = awsVolumeDao.readByInstanceNo(instanceNo);
        Instance instance = instanceDao.read(instanceNo);
        Farm farm = farmDao.read(instance.getFarmNo());
        AwsInstance awsInstance = awsInstanceDao.read(instanceNo);
        for (AwsVolume awsVolume : awsVolumes) {
            if (StringUtils.isEmpty(awsVolume.getVolumeId())) {
                continue;
            }
            IaasGatewayWrapper gateway = iaasGatewayFactory.createIaasGateway(farm.getUserNo(), awsVolume.getPlatformNo());

            //イベントログ出力
            Platform platform = platformDao.read(gateway.getPlatformNo());
            Component component = componentDao.read(awsVolume.getComponentNo());
            eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), awsVolume.getComponentNo(),
                    component.getComponentName(), instanceNo, instance.getInstanceName(), "AwsEbsDelete",
                    awsInstance.getInstanceType(), instance.getPlatformNo(), new Object[] {platform.getPlatformName(), awsVolume.getVolumeId() });

            try {
                // ボリュームの削除
                gateway.deleteVolume(awsVolume.getVolumeId());
                //awsProcessClient.waitDeleteVolume(volumeId); // TODO: EC2ではDeleteVolumeに時間がかかるため、Waitしない

                //イベントログ出力
                eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), awsVolume.getComponentNo(),
                        component.getComponentName(), instanceNo, instance.getInstanceName(), "AwsEbsDeleteFinish",
                        awsInstance.getInstanceType(), instance.getPlatformNo(), new Object[] { platform.getPlatformName(), awsVolume.getVolumeId() });

            } catch (AutoException ignore) {
                // ボリュームが存在しない場合などに備えて例外を握りつぶす
            }
        }
        awsVolumeDao.deleteByInstanceNo(instanceNo);

        // AWSインスタンスの削除処理
        ImageAws imageAws = imageAwsDao.read(instance.getImageNo());
        if (imageAws.getEbsImage() && StringUtils.isNotEmpty(awsInstance.getInstanceId())) {
            // TODO: インスタンス自体の削除処理を別で行うようにする
            IaasGatewayWrapper gateway = iaasGatewayFactory.createIaasGateway(farm.getUserNo(), instance.getPlatformNo());

            // イベントログ出力
            Platform platform = platformDao.read(gateway.getPlatformNo());
            eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo, instance
                    .getInstanceName(), "AwsInstanceDelete", awsInstance.getInstanceType(), instance.getPlatformNo(),
                    new Object[] { platform.getPlatformName(), awsInstance.getInstanceId() });

            try {
                // インスタンスの削除
                gateway.terminateInstance(awsInstance.getInstanceId());

                // イベントログ出力
                eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo,
                        instance.getInstanceName(), "AwsInstanceDeleteFinish", awsInstance.getInstanceType(), instance.getPlatformNo(),
                        new Object[] { platform.getPlatformName(), awsInstance.getInstanceId() });
            } catch (AutoException ignore) {
                // インスタンスが存在しない場合などに備えて例外を握りつぶす
            }
        }
        awsInstanceDao.deleteByInstanceNo(instanceNo);
    }

    protected void deleteVmwareInstance(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);

        // アドレスの関連を解除
        VmwareAddress vmwareAddress = vmwareAddressDao.readByInstanceNo(instanceNo);
        if (vmwareAddress != null) {
            vmwareAddressDao.delete(vmwareAddress);
        }

        VmwareProcessClient vmwareProcessClient = vmwareProcessClientFactory.createVmwareProcessClient(instance.getPlatformNo());

        try {
            // ディスクの削除
            // TODO: 削除処理を別で行うようにする
            List<VmwareDisk> vmwareDisks = vmwareDiskDao.readByInstanceNo(instanceNo);
            for (VmwareDisk vmwareDisk : vmwareDisks) {
                if (StringUtils.isEmpty(vmwareDisk.getFileName())) {
                    continue;
                }
                try {
                    // ディスクの削除
                    vmwareDiskProcess.deleteDisk(vmwareProcessClient, vmwareDisk.getDiskNo());
                } catch (AutoException ignore) {
                    // ディスクが存在しない場合などに備えて握りつぶす
                }
            }
            vmwareDiskDao.deleteByInstanceNo(instanceNo);

            // 仮想マシンの削除
            // TODO: 削除処理を別で行うようにする
            try {
                vmwareMachineProcess.destroy(vmwareProcessClient, instanceNo);
            } catch (RuntimeException ignore) {
                // TODO: 例外処理
                // 仮想マシンが存在しない場合などに備えて例外を握りつぶす
            }
        } finally {
            vmwareProcessClient.getVmwareClient().logout();
        }

        // VMwareインスタンスの削除処理
        vmwareInstanceDao.deleteByInstanceNo(instanceNo);
    }

    protected void deleteNiftyInstance(Long instanceNo) {
        // Niftyインスタンスの削除処理
        // TODO: 削除処理を別で行うようにする
        NiftyInstance niftyInstance = niftyInstanceDao.read(instanceNo);
        if (niftyInstance != null && StringUtils.isNotEmpty(niftyInstance.getInstanceId())) {
            Instance instance = instanceDao.read(instanceNo);
            Farm farm = farmDao.read(instance.getFarmNo());
            NiftyProcessClient niftyProcessClient = niftyProcessClientFactory.createNiftyProcessClient(
                    farm.getUserNo(), instance.getPlatformNo());

            try {
                niftyProcessClient.terminateInstance(niftyInstance.getInstanceId());
            } catch (RuntimeException ignore) {
                // TODO: 例外処理
                // インスタンスが存在しない場合などに備えて例外を握りつぶす
            }
        }
        niftyInstanceDao.deleteByInstanceNo(instanceNo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void associateComponents(Long instanceNo, List<Long> componentNos) {
        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }
        if (componentNos == null) {
            throw new AutoApplicationException("ECOMMON-000003", "componentNos");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // コンポーネント番号の重複を除去
        List<Long> tmpComponentNos = new ArrayList<Long>();
        for (Long componentNo : componentNos) {
            if (!tmpComponentNos.contains(componentNo)) {
                tmpComponentNos.add(componentNo);
            }
        }
        componentNos = tmpComponentNos;

        // コンポーネントの存在チェック
        List<Component> components = componentDao.readInComponentNos(componentNos);
        if (componentNos.size() != components.size()) {
            for (Component component : components) {
                componentNos.remove(component.getComponentNo());
            }
            if (componentNos.size() > 0) {
                throw new AutoApplicationException("ESERVICE-000409", componentNos.iterator().next());
            }
        }

        // MySQLのMasterでないことのチェック
        List<InstanceConfig> instanceConfigs = instanceConfigDao.readByInstanceNo(instanceNo);
        for (InstanceConfig instanceConfig : instanceConfigs) {
            if (MySQLConstants.CONFIG_NAME_MASTER_INSTANCE_NO.equals(instanceConfig.getConfigName())) {
                if (StringUtils.isEmpty(instanceConfig.getConfigValue())
                        && !componentNos.contains(instanceConfig.getComponentNo())) {
                    // MySQLのMasterで関連付けを外そうとした場合
                    throw new AutoApplicationException("ESERVICE-000414", instance.getInstanceName());
                }
            }
        }

        // コンポーネントとインスタンスの関連を更新
        List<Component> allComponents = componentDao.readByFarmNo(instance.getFarmNo());
        List<ComponentInstance> componentInstances = componentInstanceDao.readByInstanceNo(instanceNo);
        for (Component component : allComponents) {
            // コンポーネントに紐づく関連付けを取得
            ComponentInstance componentInstance = null;
            for (ComponentInstance tmpComponentInstance : componentInstances) {
                if (component.getComponentNo().equals(tmpComponentInstance.getComponentNo())) {
                    componentInstance = tmpComponentInstance;
                    break;
                }
            }

            if (componentNos.contains(component.getComponentNo())) {
                // インスタンスに関連付けるコンポーネントの場合
                if (componentInstance == null) {
                    // 関連付けレコードがない場合、レコードを作成する
                    componentInstance = new ComponentInstance();
                    componentInstance.setComponentNo(component.getComponentNo());
                    componentInstance.setInstanceNo(instanceNo);
                    componentInstance.setAssociate(true);
                    componentInstance.setEnabled(false);
                    componentInstance.setStatus(ComponentInstanceStatus.STOPPED.toString());
                    componentInstanceDao.create(componentInstance);
                } else {
                    // 関連付けレコードがある場合、関連付けを有効化する
                    if (BooleanUtils.isNotTrue(componentInstance.getAssociate())) {
                        componentInstance.setAssociate(true);
                        componentInstanceDao.update(componentInstance);
                    }
                }
            } else {
                // インスタンスに関連付けないコンポーネントの場合
                if (componentInstance != null) {
                    // 関連付けレコードがある場合
                    ComponentInstanceStatus status = ComponentInstanceStatus.fromStatus(componentInstance.getStatus());
                    if (status == ComponentInstanceStatus.STOPPED) {
                        // Zabbixのテンプレートを削除する
                        if (zabbixInstanceDao.countByInstanceNo(componentInstance.getInstanceNo()) > 0) {
                            zabbixHostProcess.removeTemplate(componentInstance.getInstanceNo(),
                                    componentInstance.getComponentNo());
                        }

                        // コンポーネントが停止している場合、関連付けを削除する
                        componentInstanceDao.delete(componentInstance);
                    } else {
                        // 関連付けを無効化する
                        if (BooleanUtils.isTrue(componentInstance.getAssociate())) {
                            componentInstance.setAssociate(false);
                            componentInstanceDao.update(componentInstance);
                        }
                    }
                }
            }
        }

        for (Component component : components) {
            ComponentType componentType = componentTypeDao.read(component.getComponentTypeNo());

            // MySQLコンポーネントの場合、Master/Slaveを設定する
            if (MySQLConstants.COMPONENT_TYPE_NAME.equals(componentType.getComponentTypeName())) {
                InstanceConfig instanceConfig = instanceConfigDao.readByInstanceNoAndComponentNoAndConfigName(
                        instanceNo, component.getComponentNo(), MySQLConstants.CONFIG_NAME_MASTER_INSTANCE_NO);
                if (instanceConfig == null) {
                    // Masterのインスタンスを取得
                    Long masterInstanceNo = null;
                    List<InstanceConfig> configs = instanceConfigDao.readByComponentNo(component.getComponentNo());
                    for (InstanceConfig config : configs) {
                        if (MySQLConstants.CONFIG_NAME_MASTER_INSTANCE_NO.equals(config.getConfigName())) {
                            if (StringUtils.isEmpty(config.getConfigValue())) {
                                masterInstanceNo = config.getInstanceNo();
                                break;
                            }
                        }
                    }

                    // Masterのインスタンスが存在しない場合はMaster、存在する場合はSlaveにする
                    instanceConfig = new InstanceConfig();
                    instanceConfig.setInstanceNo(instanceNo);
                    instanceConfig.setComponentNo(component.getComponentNo());
                    instanceConfig.setConfigName(MySQLConstants.CONFIG_NAME_MASTER_INSTANCE_NO);
                    if (masterInstanceNo == null) {
                        instanceConfig.setConfigValue(null);
                    } else {
                        instanceConfig.setConfigValue(masterInstanceNo.toString());
                    }
                    instanceConfigDao.create(instanceConfig);
                }
            }
        }

        // イベントログ出力
        StringBuilder names = new StringBuilder();
        for (Component component : components) {
            names.append(component.getComponentName()).append(",");
        }
        if (names.length() > 0) {
            names.deleteCharAt(names.length() - 1);
        }
        Farm farm = farmDao.read(instance.getFarmNo());
        eventLogger.log(EventLogLevel.DEBUG, farm.getFarmNo(), farm.getFarmName(), null, null, instanceNo, instance.getInstanceName(),
                "InstanceAssociateComponent", null, instance.getPlatformNo(), new Object[] { names.toString() });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PlatformDto> getPlatforms(Long userNo) {
        // プラットフォームを取得
        List<PlatformDto> dtos = new ArrayList<PlatformDto>();
        List<Platform> platforms = platformDao.readAll();
        List<ComponentType> componentTypes = componentTypeDao.readAll();
        List<Image> images = imageDao.readAll();
        for (Platform platform : platforms) {
            PlatformAws platformAws = null;
            PlatformVmware platformVmware = null;
            PlatformNifty platformNifty = null;
            PlatformCloudstack platformCloudstack = null;
            if ("aws".equals(platform.getPlatformType())) {
                // AWSの認証情報がない場合はスキップ
                if (awsCertificateDao.countByUserNoAndPlatformNo(userNo, platform.getPlatformNo()) == 0) {
                    continue;
                }
                platformAws = platformAwsDao.read(platform.getPlatformNo());
            } else if ("vmware".equals(platform.getPlatformType())) {
                // キーペアがない場合はスキップ
                // TODO: 権限を別途持つ
                if (vmwareKeyPairDao.countByUserNoAndPlatformNo(userNo, platform.getPlatformNo()) == 0) {
                    continue;
                }
                platformVmware = platformVmwareDao.read(platform.getPlatformNo());
            } else if ("nifty".equals(platform.getPlatformType())) {
                // 認証情報とキーペアがない場合はスキップ
                // TODO: 権限を別途持つ
                if (niftyCertificateDao.countByUserNoAndPlatformNo(userNo, platform.getPlatformNo()) == 0) {
                    continue;
                }
                if (niftyKeyPairDao.countByUserNoAndPlatformNo(userNo, platform.getPlatformNo()) == 0) {
                    continue;
                }
                platformNifty = platformNiftyDao.read(platform.getPlatformNo());
            } else if ("cloudstack".equals(platform.getPlatformType())) {
                // 認証情報とキーペアがない場合はスキップ
                // TODO: 権限を別途持つ
                if (cloudstackCertificateDao.countByAccountAndPlatformNo(userNo, platform.getPlatformNo()) == 0) {
                    continue;
                }
                platformCloudstack = platformCloudstackDao.read(platform.getPlatformNo());
            }

            List<ImageDto> imageDtos = getImages(platform, images, componentTypes);

            PlatformDto dto = new PlatformDto();
            dto.setPlatform(platform);
            dto.setPlatformAws(platformAws);
            dto.setPlatformVmware(platformVmware);
            dto.setPlatformNifty(platformNifty);
            dto.setPlatformCloudstack(platformCloudstack);
            dto.setImages(imageDtos);
            dtos.add(dto);
        }

        return dtos;
    }

    protected List<ImageDto> getImages(Platform platform, List<Image> images, List<ComponentType> allComponentTypes) {
        // イメージを取得
        List<ImageDto> imageDtos = new ArrayList<ImageDto>();
        for (Image image : images) {
            // プラットフォームが異なる場合はスキップ
            if (platform.getPlatformNo().equals(image.getPlatformNo()) == false) {
                continue;
            }

            ImageAws imageAws = null;
            ImageCloudstack imageCloudstack = null;
            ImageVmware imageVmware = null;
            ImageNifty imageNifty = null;
            if ("aws".equals(platform.getPlatformType())) {
                imageAws = imageAwsDao.read(image.getImageNo());
            } else if ("cloudstack".equals(platform.getPlatformType())) {
                imageCloudstack = imageCloudstackDao.read(image.getImageNo());
            } else if ("vmware".equals(platform.getPlatformType())) {
                imageVmware = imageVmwareDao.read(image.getImageNo());
            } else if ("nifty".equals(platform.getPlatformType())) {
                imageNifty = imageNiftyDao.read(image.getImageNo());
            }

            // イメージに対応したコンポーネントタイプを取得
            String[] componentTypeNos = StringUtils.split(image.getComponentTypeNos(), ",");
            List<ComponentType> componentTypes = new ArrayList<ComponentType>();
            for (String componentTypeNo : componentTypeNos) {
                long no = Long.valueOf(componentTypeNo.trim());
                for (ComponentType componentType: allComponentTypes) {
                    if (no == componentType.getComponentTypeNo().longValue()) {
                        componentTypes.add(componentType);
                    }
                }
            }

            ImageDto imageDto = new ImageDto();
            imageDto.setImage(image);
            imageDto.setImageAws(imageAws);
            imageDto.setImageCloudstack(imageCloudstack);
            imageDto.setImageVmware(imageVmware);
            imageDto.setImageNifty(imageNifty);
            imageDto.setComponentTypes(componentTypes);
            imageDtos.add(imageDto);
        }

        return imageDtos;
    }

    protected String createUrl(String ipAddress, Long componentTypeNo) {

//        String url = "http://";
//        ComponentType componentType = componentTypeDao.read(componentTypeNo);
//        if ("apache".equals(componentType.getComponentTypeName())) {
//            url = url + ipAddress + ":80/";
//        } else if ("tomcat".equals(componentType.getComponentTypeName())){
//            url = url + ipAddress + ":8080/";
//        } else if ("geronimo".equals(componentType.getComponentTypeName())) {
//            url = url + ipAddress + ":8080/console/";
//        } else if ("mysql".equals(componentType.getComponentTypeName())) {
//            url = url + ipAddress + ":8085/phpmyadmin/";
//        } else if ("prjserver".equals(componentType.getComponentTypeName())) {
//            url = url + ipAddress + "/trac/prj/top/";
//        }


        ComponentType componentType = componentTypeDao.read(componentTypeNo);
        String url = componentType.getAddressUrl();
        url = url.replaceAll("%d", ipAddress);

        return url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enableZabbixMonitoring(Long instanceNo) {
        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            // インスタンスが存在しない場合
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // ZABBIX_INSTANCEの存在チェック
        ZabbixInstance zabbixInstance = zabbixInstanceDao.read(instanceNo);
        if (zabbixInstance == null) {
            // インスタンスが存在しない場合
            throw new AutoApplicationException("ESERVICE-000422", instanceNo);
        }

        // Zabbix使用フラグ(config.properties、zabbix.useZabbix)のチェック
        Boolean useZabbix = BooleanUtils.toBooleanObject(Config.getProperty("zabbix.useZabbix"));
        if (BooleanUtils.isNotTrue(useZabbix)) {
            // Zabbix使用フラグが「true」以外
            throw new AutoApplicationException("ESERVICE-000423", instanceNo);
        }

        // サーバOSステータスのチェック
        InstanceStatus instanceStatus = getInstanceStatus(instance);
        if (instanceStatus != InstanceStatus.RUNNING) {
            // サーバOSステータスが「RUNNING」以外
            throw new AutoApplicationException("ESERVICE-000424", instance.getInstanceName());
        }

        // Zabbixステータスのチェック
        ZabbixInstanceStatus zabbixInstanceStatus = ZabbixInstanceStatus.fromStatus(zabbixInstance.getStatus());
        if (zabbixInstanceStatus != ZabbixInstanceStatus.UN_MONITORING) {
            // Zabbixの監視ステータスが「UN_MONITORING」以外
            throw new AutoApplicationException("ESERVICE-000425", instance.getInstanceName());
        }

        // Zabbix有効化
        zabbixHostProcess.startHost(instanceNo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableZabbixMonitoring(Long instanceNo) {
        // 引数チェック
        if (instanceNo == null) {
            throw new AutoApplicationException("ECOMMON-000003", "instanceNo");
        }

        // インスタンスの存在チェック
        Instance instance = instanceDao.read(instanceNo);
        if (instance == null) {
            // インスタンスが存在しない場合
            throw new AutoApplicationException("ESERVICE-000403", instanceNo);
        }

        // ZABBIX_INSTANCEの存在チェック
        ZabbixInstance zabbixInstance = zabbixInstanceDao.read(instanceNo);
        if (zabbixInstance == null) {
            // インスタンスが存在しない場合
            throw new AutoApplicationException("ESERVICE-000422", instanceNo);
        }

        // Zabbix使用フラグ(config.properties、zabbix.useZabbix)のチェック
        Boolean useZabbix = BooleanUtils.toBooleanObject(Config.getProperty("zabbix.useZabbix"));
        if (BooleanUtils.isNotTrue(useZabbix)) {
            // Zabbix使用フラグが「true」以外
            throw new AutoApplicationException("ESERVICE-000423", instanceNo);
        }

        // サーバOSステータスのチェック
        InstanceStatus instanceStatus = getInstanceStatus(instance);
        if (instanceStatus != InstanceStatus.RUNNING) {
            // サーバOSステータスが「RUNNING」以外
            throw new AutoApplicationException("ESERVICE-000426", instance.getInstanceName());
        }

        // Zabbixステータスのチェック
        ZabbixInstanceStatus zabbixInstanceStatus = ZabbixInstanceStatus.fromStatus(zabbixInstance.getStatus());
        if (zabbixInstanceStatus != ZabbixInstanceStatus.MONITORING) {
            // Zabbixの監視ステータスが「MONITORING」以外
            throw new AutoApplicationException("ESERVICE-000427", instance.getInstanceName());
        }

        // Zabbix無効化
        zabbixHostProcess.stopHost(instanceNo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InstanceStatus getInstanceStatus(Instance instance) {
        // 有効無効に応じてステータスを変更する（画面表示用）
        InstanceStatus instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
        if (BooleanUtils.isTrue(instance.getEnabled())) {
            if (instanceStatus == InstanceStatus.STOPPED) {
                instance.setStatus(InstanceStatus.STARTING.toString());
            }
        } else {
            if (instanceStatus == InstanceStatus.RUNNING || instanceStatus == InstanceStatus.WARNING) {
                instance.setStatus(InstanceStatus.STOPPING.toString());
            }
        }

        // 画面表示用にステータスの変更
        //    サーバステータス 協調設定ステータス   変換後サーバステータス
        //        Running         Coodinating            Configuring
        //        Running         Warning                Warning
        instanceStatus = InstanceStatus.fromStatus(instance.getStatus());
        InstanceCoodinateStatus insCoodiStatus = InstanceCoodinateStatus.fromStatus(instance.getCoodinateStatus());
        // サーバステータス(Running)かつ協調設定ステータス(Coodinating)⇒「Configuring」
        if (instanceStatus == InstanceStatus.RUNNING && insCoodiStatus == InstanceCoodinateStatus.COODINATING) {
            instance.setStatus(InstanceStatus.CONFIGURING.toString());
        // サーバステータス(Running)かつ協調設定ステータス(Warning)⇒「Warning」
        } else if (instanceStatus == InstanceStatus.RUNNING && insCoodiStatus == InstanceCoodinateStatus.WARNING) {
            instance.setStatus(InstanceStatus.WARNING.toString());
        }

        return InstanceStatus.fromStatus(instance.getStatus());
    }

    /**
     * awsProcessClientFactoryを設定します。
     *
     * @param awsProcessClientFactory awsProcessClientFactory
     */
    public void setIaasGatewayFactory(IaasGatewayFactory iaasGatewayFactory) {
        this.iaasGatewayFactory = iaasGatewayFactory;
    }

    /**
     * awsDescribeServiceを設定します。
     *
     * @param awsDescribeService awsDescribeService
     */
    public void setAwsDescribeService(IaasDescribeService awsDescribeService) {
        this.iaasDescribeService = awsDescribeService;
    }

    /**
     * vmwareDescribeServiceを設定します。
     *
     * @param vmwareDescribeService vmwareDescribeService
     */
    public void setVmwareDescribeService(VmwareDescribeService vmwareDescribeService) {
        this.vmwareDescribeService = vmwareDescribeService;
    }

    /**
     * vmwareMachineProcessを設定します。
     *
     * @param vmwareMachineProcess vmwareMachineProcess
     */
    public void setVmwareMachineProcess(VmwareMachineProcess vmwareMachineProcess) {
        this.vmwareMachineProcess = vmwareMachineProcess;
    }

    /**
     * vmwareDiskProcessを設定します。
     *
     * @param vmwareDiskProcess vmwareDiskProcess
     */
    public void setVmwareDiskProcess(VmwareDiskProcess vmwareDiskProcess) {
        this.vmwareDiskProcess = vmwareDiskProcess;
    }

    /**
     * vmwareProcessClientFactoryを設定します。
     *
     * @param vmwareProcessClientFactory vmwareProcessClientFactory
     */
    public void setVmwareProcessClientFactory(VmwareProcessClientFactory vmwareProcessClientFactory) {
        this.vmwareProcessClientFactory = vmwareProcessClientFactory;
    }

    /**
     * niftyProcessClientFactoryを設定します。
     *
     * @param niftyProcessClientFactory niftyProcessClientFactory
     */
    public void setNiftyProcessClientFactory(NiftyProcessClientFactory niftyProcessClientFactory) {
        this.niftyProcessClientFactory = niftyProcessClientFactory;
    }

    /**
     * zabbixProcessClientFactoryを設定します。
     *
     * @param zabbixProcessClientFactory zabbixProcessClientFactory
     */
    public void setZabbixProcessClientFactory(ZabbixProcessClientFactory zabbixProcessClientFactory) {
        this.zabbixProcessClientFactory = zabbixProcessClientFactory;
    }

    /**
     * zabbixHostProcessを設定します。
     *
     * @param zabbixHostProcess zabbixHostProcess
     */
    public void setZabbixHostProcess(ZabbixHostProcess zabbixHostProcess) {
        this.zabbixHostProcess = zabbixHostProcess;
    }

    /**
     * eventLoggerを設定します。
     *
     * @param eventLogger eventLogger
     */
    public void setEventLogger(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

}
