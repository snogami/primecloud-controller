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
package jp.primecloud.auto.process;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jp.primecloud.auto.common.component.DnsStrategy;
import jp.primecloud.auto.entity.crud.AwsInstance;
import jp.primecloud.auto.entity.crud.CloudstackInstance;
import jp.primecloud.auto.entity.crud.Image;
import jp.primecloud.auto.entity.crud.Instance;
import jp.primecloud.auto.entity.crud.Platform;
import jp.primecloud.auto.entity.crud.PlatformAws;
import jp.primecloud.auto.exception.AutoException;
import jp.primecloud.auto.log.EventLogger;
import jp.primecloud.auto.service.ServiceSupport;
import jp.primecloud.auto.util.MessageUtils;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;


/**
 * <p>
 * TODO: クラスコメントを記述
 * </p>
 *
 */
public class DnsProcess extends ServiceSupport {

    protected DnsStrategy dnsStrategy;

    protected boolean reverseEnabled = true;

    protected ProcessLogger processLogger;

    protected EventLogger eventLogger;

    public void startDns(Platform platform, Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);
        if ("cloudstack".equals(platform.getPlatformType())) {
            startDnsCloudstack(instanceNo);
        }else if ("aws".equals(platform.getPlatformType())) {
            //Platform platform = Config.getPlatform(awsProcessClient.getPlatformNo());
            if (platform.getInternal()) {
                // 内部のプラットフォームの場合
                PlatformAws platformAws = platformAwsDao.read(platform.getPlatformNo());
                if (BooleanUtils.isTrue(platformAws.getEuca())) {
                    // Eucalyptusの場合
                    startDnsNormalEuca(instanceNo);
                } else {
                    // Amazon EC2の場合
                    startDnsNormalEc2(instanceNo);
                }
            } else {
                // 外部のプラットフォームの場合
                AwsInstance awsInstance = awsInstanceDao.read(instanceNo);
                if (!StringUtils.isEmpty(awsInstance.getSubnetId())) {
                    // VPCを使用している場合
                    startDnsVpc(instanceNo);
                } else {
                    // Windowsの場合はVPNの使用なし
                    Image image = imageDao.read(instance.getImageNo());
                    if (StringUtils.startsWithIgnoreCase(image.getOs(), "windows")) {
                        // VPNを使用していない場合
                        startDnsNormalEc2(instanceNo);
                    } else {
                        // VPNを使用している場合
                        startDnsVpn(instanceNo);
                    }
                }
            }
        }
        // イベントログ出力
        instance = instanceDao.read(instanceNo);
        processLogger.writeLogSupport(ProcessLogger.LOG_DEBUG, null, instance, "DnsRegist", new Object[] { instance.getFqdn(), instance.getPublicIp() });
    }

    public void stopDns(Platform platform, Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);
        if ("cloudstack".equals(platform.getPlatformType())) {
            stopDnsCloudstack(instanceNo);
        }else if ("aws".equals(platform.getPlatformType())) {
            //Platform platform = Config.getPlatform(awsProcessClient.getPlatformNo());
            if (platform.getInternal()) {
                // 内部のプラットフォームの場合
                PlatformAws platformAws = platformAwsDao.read(platform.getPlatformNo());
                if (BooleanUtils.isTrue(platformAws.getEuca())) {
                    // Eucalyptusの場合
                    stopDnsNormalEuca(instanceNo);
                } else {
                    // Amazon EC2の場合
                    stopDnsNormalEc2(instanceNo);
                }
            } else {
                // 外部のプラットフォームの場合
                AwsInstance awsInstance = awsInstanceDao.read(instanceNo);
                if (!StringUtils.isEmpty(awsInstance.getSubnetId())) {
                    // VPCを使用している場合
                    stopDnsVpc(instanceNo);
                } else {
                    // Windowsの場合はVPNの使用なし
                    Image image = imageDao.read(instance.getImageNo());
                    if (StringUtils.startsWithIgnoreCase(image.getOs(), "windows")) {
                        stopDnsNormalEc2(instanceNo);
                    } else {
                        // VPNを使用している場合
                        stopDnsVpn(instanceNo);
                    }
                }
            }
        }

        // イベントログ出力
        processLogger.writeLogSupport(ProcessLogger.LOG_DEBUG, null, instance, "DnsUnregist", new Object[] { instance.getFqdn(), instance.getPublicIp() });
    }

    protected void startDnsNormalEuca(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);
        AwsInstance awsInstance = awsInstanceDao.read(instanceNo);

        // 最新のAwsInstance情報がInstanceに登録されている場合はスキップする
        if (StringUtils.equals(instance.getPublicIp(), awsInstance.getDnsName())) {
            return;
        }

        // DnsName, PrivateDnsNameをそのままIPアドレスとして使用する
        String fqdn = instance.getFqdn();
        String publicIp = awsInstance.getDnsName();
        String privateIp = awsInstance.getPrivateDnsName();

        // 正引きの追加
        addForward(fqdn, publicIp);

        // 逆引きの追加
        addReverse(fqdn, publicIp);

        // データベースの更新
        instance.setPublicIp(publicIp);
        instance.setPrivateIp(privateIp);
        instanceDao.update(instance);
    }

    protected void startDnsNormalEc2(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);
        AwsInstance awsInstance = awsInstanceDao.read(instanceNo);

        // 最新のAwsInstance情報がInstanceに登録されている場合はスキップする
        if (StringUtils.equals(instance.getPublicIp(), awsInstance.getDnsName())) {
            return;
        }

        String fqdn = instance.getFqdn();
        String publicIp = awsInstance.getIpAddress();
        String privateIp = awsInstance.getPrivateIpAddress();

        // CNAMEの追加
        addCanonicalName(fqdn, awsInstance.getDnsName());

        // データベースの更新
        instance.setPublicIp(publicIp);
        instance.setPrivateIp(privateIp);
        instanceDao.update(instance);
    }

    protected void startDnsCloudstack(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);
        CloudstackInstance csInstance = cloudstackInstanceDao.read(instanceNo);

        // 最新のAwsInstance情報がInstanceに登録されている場合はスキップする
        if (StringUtils.equals(instance.getPublicIp(), csInstance.getIpaddress())) {
            return;
        }

        String fqdn = instance.getFqdn();
        String publicIp = resolveHost(fqdn);
        String privateIp = csInstance.getIpaddress();

        // 逆引きの追加
        addReverse(fqdn, publicIp);

        // データベースの更新
        instance.setPublicIp(publicIp);
        instance.setPrivateIp(privateIp);
        instanceDao.update(instance);
    }


    protected void startDnsVpn(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);
        AwsInstance awsInstance = awsInstanceDao.read(instanceNo);

        // InstanceにIPアドレスが登録済みの場合はスキップする
        if (!StringUtils.isEmpty(instance.getPublicIp())) {
            return;
        }

        // IPアドレスを正引きにより取得する（正引きの追加はインスタンス内で行う）
        String fqdn = instance.getFqdn();
        String publicIp = resolveHost(fqdn); // VPNインタフェースのIPアドレス
        String privateIp = awsInstance.getPrivateIpAddress();

        // 逆引きの追加
        addReverse(fqdn, publicIp);

        // データベースの更新
        instance.setPublicIp(publicIp);
        instance.setPrivateIp(privateIp);
        instanceDao.update(instance);
    }

    protected void startDnsVpc(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);
        AwsInstance awsInstance = awsInstanceDao.read(instanceNo);

        // InstanceにIPアドレスが登録済みの場合はスキップする
        if (!StringUtils.isEmpty(instance.getPublicIp())) {
            return;
        }

        // PrivateIpAddressをIPアドレスとする
        String fqdn = instance.getFqdn();
        String publicIp = awsInstance.getPrivateIpAddress();
        String privateIp = awsInstance.getPrivateIpAddress();

        // 正引きの追加
        addForward(fqdn, publicIp);

        // 逆引きの追加
        addReverse(fqdn, publicIp);

        // データベースの更新
        instance.setPublicIp(publicIp);
        instance.setPrivateIp(privateIp);
        instanceDao.update(instance);
    }

    protected void stopDnsNormalEuca(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);

        // IPアドレスがない場合はスキップ
        if (StringUtils.isEmpty(instance.getPublicIp())) {
            return;
        }

        String fqdn = instance.getFqdn();
        String publicIp = instance.getPublicIp();

        // 正引きの削除
        deleteForward(fqdn);

        // 逆引きの削除
        deleteReverse(publicIp);

        // データベースの更新
        instance.setPublicIp(null);
        instance.setPrivateIp(null);
        instanceDao.update(instance);
    }

    protected void stopDnsNormalEc2(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);

        // IPアドレスがない場合はスキップ
        if (StringUtils.isEmpty(instance.getPublicIp())) {
            return;
        }

        String fqdn = instance.getFqdn();

        // CNAMEの削除
        deleteCanonicalName(fqdn);

        // データベースの更新
        instance.setPublicIp(null);
        instance.setPrivateIp(null);
        instanceDao.update(instance);
    }

    protected void stopDnsCloudstack(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);

        // IPアドレスがない場合はスキップ
        if (StringUtils.isEmpty(instance.getPublicIp())) {
            return;
        }

        String fqdn = instance.getFqdn();
        String publicIp = instance.getPublicIp();

        // CNAMEの削除
        //deleteCanonicalName(fqdn);

        // 正引きの削除
        deleteForward(fqdn);
        // 逆引きの削除
        deleteReverse(publicIp);

        // データベースの更新
        instance.setPublicIp(null);
        instance.setPrivateIp(null);
        instanceDao.update(instance);
    }

    protected void stopDnsVpn(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);

        // IPアドレスがない場合はスキップ
        if (StringUtils.isEmpty(instance.getPublicIp())) {
            return;
        }

        String fqdn = instance.getFqdn();
        String publicIp = instance.getPublicIp();

        // 正引きの削除
        deleteForward(fqdn);

        // 逆引きの削除
        deleteReverse(publicIp);

        // データベースの更新
        instance.setPublicIp(null);
        instance.setPrivateIp(null);
        instanceDao.update(instance);
    }

    protected void stopDnsVpc(Long instanceNo) {
        Instance instance = instanceDao.read(instanceNo);

        // IPアドレスがない場合はスキップ
        if (StringUtils.isEmpty(instance.getPublicIp())) {
            return;
        }

        String fqdn = instance.getFqdn();
        String publicIp = instance.getPublicIp();

        // 正引きの削除
        deleteForward(fqdn);

        // 逆引きの削除
        deleteReverse(publicIp);

        // データベースの更新
        instance.setPublicIp(null);
        instance.setPrivateIp(null);
        instanceDao.update(instance);
    }

    protected void addForward(String fqdn, String publicIp) {
        // 正引きの追加
        dnsStrategy.addForward(fqdn, publicIp);

        // ログ出力
        if (log.isInfoEnabled()) {
            log.info(MessageUtils.getMessage("IPROCESS-100141", fqdn, publicIp));
        }
    }

    protected void addReverse(String fqdn, String publicIp) {
        if (!reverseEnabled) {
            // 逆引きが無効の場合はスキップ
            return;
        }

        // 逆引きの追加
        dnsStrategy.addReverse(fqdn, publicIp);

        // ログ出力
        if (log.isInfoEnabled()) {
            log.info(MessageUtils.getMessage("IPROCESS-100142", publicIp, fqdn));
        }
    }

    protected void addCanonicalName(String fqdn, String canonicalName) {
        // CNAMEの追加
        dnsStrategy.addCanonicalName(fqdn, canonicalName);

        // ログ出力
        if (log.isInfoEnabled()) {
            log.info(MessageUtils.getMessage("IPROCESS-100145", fqdn, canonicalName));
        }
    }

    protected void deleteForward(String fqdn) {
        // 正引きの削除
        dnsStrategy.deleteForward(fqdn);

        // ログ出力
        if (log.isInfoEnabled()) {
            log.info(MessageUtils.getMessage("IPROCESS-100143", fqdn));
        }
    }

    protected void deleteReverse(String publicIp) {
        if (!reverseEnabled) {
            // 逆引きが無効の場合はスキップ
            return;
        }

        // 逆引きの削除
        dnsStrategy.deleteReverse(publicIp);

        // ログ出力
        if (log.isInfoEnabled()) {
            log.info(MessageUtils.getMessage("IPROCESS-100144", publicIp));
        }
    }

    protected void deleteCanonicalName(String fqdn) {
        // CNAMEの削除
        dnsStrategy.deleteCanonicalName(fqdn);

        // ログ出力
        if (log.isInfoEnabled()) {
            log.info(MessageUtils.getMessage("IPROCESS-100146", fqdn));
        }
    }

    protected String resolveHost(String fqdn) {
        long timeout = 1000L * 60 * 5;
        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                InetAddress address = InetAddress.getByName(fqdn);
                return address.getHostAddress();
            } catch (UnknownHostException ignore) {
            }
            if (System.currentTimeMillis() - startTime > timeout) {
                // タイムアウト発生時
                throw new AutoException("EPROCESS-000205", fqdn);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignore) {
            }
        }
    }

    /**
     * dnsStrategyを設定します。
     *
     * @param dnsStrategy dnsStrategy
     */
    public void setDnsStrategy(DnsStrategy dnsStrategy) {
        this.dnsStrategy = dnsStrategy;
    }

    /**
     * reverseEnabledを設定します。
     *
     * @param reverseEnabled reverseEnabled
     */
    public void setReverseEnabled(boolean reverseEnabled) {
        this.reverseEnabled = reverseEnabled;
    }

    /**
     * eventLoggerを設定します。
     *
     * @param eventLogger eventLogger
     */
    public void setEventLogger(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    /**
     * processLoggerを設定します。
     *
     * @param processLogger processLogger
     */
    public void setProcessLogger(ProcessLogger processLogger) {
        this.processLogger = processLogger;
    }

}
