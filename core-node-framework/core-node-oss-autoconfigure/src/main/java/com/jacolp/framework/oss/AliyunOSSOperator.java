package com.jacolp.framework.oss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.UUID;

import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;

public class AliyunOSSOperator {
    AliyunOSSClient aliyunOSSClient;
    AliyunOSSProperties aliyunOSSProperties;

    public AliyunOSSOperator(AliyunOSSProperties aliyunOSSProperties, AliyunOSSClient aliyunOSSClient) {
        this.aliyunOSSClient = aliyunOSSClient;
        this.aliyunOSSProperties = aliyunOSSProperties;
    }

    /**
     * 使用字节数组进行文件上传
     * 上传之后文件名为：UUID + 文件原始名称的后缀
     *
     * @param content          文件的字节数组
     * @param originalFileName 文件的原始名称(用于获取文件名的后缀)
     * @return 文件上传之后的在线访问 url (endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + objectName)
     */
    public String upload(byte[] content, String originalFileName, String directory) {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();

        // 设置文件完整路径
        // 生成一个新的不重复的文件名
        String newFileName = UUID.randomUUID() + originalFileName.substring(originalFileName.lastIndexOf("."));
        String objectName = directory + "/" + newFileName;

        OSS ossClient = aliyunOSSClient.getOssClient();

        try {
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, new ByteArrayInputStream(content));

            // 创建PutObject请求。
            PutObjectResult result = ossClient.putObject(putObjectRequest);
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        }

        // 返回可以访问已上传文件的拼装 url 路径
        return endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + objectName;
    }

    /**
     * 使用固定 object key 上传文件。
     * 说明：与 upload 方法不同，此方法不会自动追加 UUID，适合调用方按规范自行拼装 key。
     *
     * @param content 文件字节数组
     * @param objectName 完整 object key，例如 image/{userId}/{filename}
     * @return 上传后的访问 URL
     */
    public String uploadByObjectName(byte[] content, String objectName) {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();

        OSS ossClient = aliyunOSSClient.getOssClient();

        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, new ByteArrayInputStream(content));
            PutObjectResult result = ossClient.putObject(putObjectRequest);
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        }

        return endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + objectName;
    }


    /**
     * 删除文件
     *
     * @param objectName 完整文件路径
     * @return 删除结果
     */
    public Boolean delete(String objectName) {
        String bucketName = aliyunOSSProperties.getBucketName();

        OSS ossClient = aliyunOSSClient.getOssClient();

        try {
            // 删除文件或目录。如果要删除目录，目录必须为空。
            ossClient.deleteObject(bucketName, objectName);
        } catch (OSSException oe) {
            return false;
        } catch (ClientException ce) {
            return false;
        }

        return true;
    }


    /**
     * 下载文件
     *
     * @param objectName  云OSS上完整文件路径
     * @param storagePath 文件保存到本地路径
     * @return 是否保存成功
     */
    public boolean download(String objectName, String storagePath) {
        String bucketName = aliyunOSSProperties.getBucketName();

        OSS ossClient = aliyunOSSClient.getOssClient();

        try {
            // 下载Object到本地文件，并保存到指定的本地路径中。如果指定的本地文件存在会覆盖，不存在则新建。
            // 如果未指定本地路径，则下载后的文件默认保存到示例程序所属项目对应本地路径中。
            ossClient.getObject(new GetObjectRequest(bucketName, objectName), new File(storagePath));
            return true;
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        }

        return false;
    }
}
