package nju.jgd.seaweedfs_demo;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 用Amazon的s3 SDK与SeaweedFS交互
 */
public class SeaweedFSDemo {

    // 使用多线程并行上传一个目录中的多个文件，提高性能
    private static final Integer THREAD_COUNT = new Integer(4);

    private static final Logger logger = Logger.getLogger("SeaweedFSLogger");

    // 与SeaweedFS交互的客户端
    private static S3Client client;

    static {
        try {
            client = S3Client.builder()
                    .endpointOverride(URI.create("http://210.28.132.20:8333"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("admin", "Fixed0630")
                    ))
                    // 或者
                    // export AWS_ACCESS_KEY_ID=access-key
                    // export AWS_SECRET_ACCESS_KEY=secret-key
                    .region(Region.US_EAST_1) // SeaweedFS不强制区域校验
                    .forcePathStyle(true) // 强制路径风格,避免DNS路径解析问题
                    .build();
            System.out.println("SeaweedFS client created.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static S3Client getClient() {
        return client;
    }

    // 创建一个新的桶
    public static void createBucket(S3Client client, String bucketName) {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            System.out.println("Successfully created bucket: " + bucketName);
        } catch (AwsServiceException e) {
            throw new RuntimeException(e);
        }
    }

    // 上传一个文件到SeaweedFS的某个桶中
    public static void uploadObject(S3Client client, String bucketName, String objectName, String input) {
        client.putObject(PutObjectRequest.builder().bucket(bucketName).key(objectName).build(), Paths.get(input));
        System.out.println("Successfully uploaded " + objectName + " to " + bucketName);
    }

    // 从SeaweedFS的某个桶中下载一个文件
    public static void downloadObject(S3Client client, String bucketName, String objectName, String output) {
        client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectName).build(), Paths.get(output));
        System.out.println("Successfully downloaded " + objectName + " from " + bucketName);
    }

    // 删除SeaweedFS的某个桶中的一个文件
    public static void deleteObject(S3Client client, String bucketName, String objectName) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectName).build());
        System.out.println("Successfully deleted " + objectName + " from " + bucketName);
    }

    // 列出所有的桶
    public static void listBuckets(S3Client client) {
        ListBucketsResponse listBucketsResponse = client.listBuckets();
        for (Bucket bucket : listBucketsResponse.buckets()) {
            System.out.println(bucket.name());
        }
    }

    // 列出某个桶中的所有对象
    public static void listObjects(S3Client client, String bucketName) {
        ListObjectsV2Response listObjectsV2Response = client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).build());
        int cnt = 0;
        for (S3Object content : listObjectsV2Response.contents()) {
            cnt ++;
            // System.out.println(content.key());
            logger.info(cnt + ": " + content.key());
        }
        System.out.println("Number of objects in bucket: " + cnt);
    }

    // 批量上传一个目录下的多个文件（递归，包含子目录中的文件）到SeaweedFS的某个桶中，使用多线程
    public static void uploadDirectory(S3Client client, String bucketName, String directory) {
        // 要上传的目录
        File baseDir = new File(directory);
        if (!baseDir.exists() || !baseDir.isDirectory()){
            System.out.println("Directory " + directory + " does not exist or is not a directory.");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        uploadRecursively(client, bucketName, baseDir.getName(), directory, executor);

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void uploadRecursively(S3Client client, String bucketName, String prefix, String directory, ExecutorService executor) {
        File baseDir = new File(directory);
        for (File file : baseDir.listFiles()) {
            if (file.isFile()) {
                // 构建SeaweedFS中的存储路径，保持目录结构
                String localFilePath = new File(directory, file.getName()).getPath();
                String seaweedfsPath = new File(prefix, file.getName()).getPath();
                // 将该文件上传到SeaweedFS中
                executor.submit(() -> uploadObject(client, bucketName, seaweedfsPath, localFilePath));
            } else if(file.isDirectory()) {
                uploadRecursively(client, bucketName, new File(prefix, file.getName()).getPath(), new File(directory, file.getName()).getPath(), executor);
            }
        }
    }

    // /**
    //  * 下载一个目录到本地<br/>
    //  * @param client    S3客户端
    //  * @param bucketName    桶名
    //  * @param prefix    要下载的对象的前缀
    //  * @param localDirectory 本地保存的目录
    //  */
    // public static void downloadDirectory(S3Client client, String bucketName, String prefix, String localDirectory) {
    //     ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build();
    //     ListObjectsV2Response listObjectsV2Response;
    //     do {
    //         listObjectsV2Response = client.listObjectsV2(listObjectsV2Request);
    //         for (S3Object object : listObjectsV2Response.contents()) {
    //             String objectKey = object.key();
    //             // 去掉对象存储的前缀后的相对路径
    //             // 例如：base/a/a.txt -> a/a.txt
    //             String relativePath = objectKey.substring(prefix.length());
    //             // 添加本地路径前缀，构造本地文件路径
    //             File localFilePath = new File(localDirectory, relativePath);
    //             // 确保父目录存在
    //             localFilePath.getParentFile().mkdirs();
    //             // 下载对象到本地
    //             downloadObject(client, bucketName, objectKey, localFilePath.getPath());
    //             System.out.println("Successfully downloaded " + objectKey + " to " + localDirectory);
    //         }
    //     } while (listObjectsV2Response.isTruncated());
    // }

    /**
     * 下载一个目录到本地（增强版）
     * @param client          S3客户端
     * @param bucketName      桶名
     * @param prefix          要下载的对象前缀（自动添加末尾斜杠）
     * @param localDirectory  本地保存目录
     */
    public static void downloadDirectory(S3Client client, String bucketName, String prefix, String localDirectory) {
        // 自动修正 prefix 格式：确保以 "/" 结尾
        if (prefix == null) prefix = "";
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix += "/";
        }

        // 初始化分页请求
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(1000) // 与服务端分页大小一致
                .build();

        ListObjectsV2Response listResponse;
        int pageCount = 0;
        do {
            pageCount++;
            listResponse = client.listObjectsV2(listRequest);
            System.out.printf("Processing page %d, ContinuationToken: %s%n",
                    pageCount, listRequest.continuationToken());

            // 遍历当前页对象
            for (S3Object object : listResponse.contents()) {
                String objectKey = object.key();
                System.out.println("Downloading object: " + objectKey);

                // 生成本地相对路径（确保去掉 prefix 后的路径正确）
                String relativePath = objectKey.substring(prefix.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1); // 避免路径以 "/" 开头
                }

                File localFile = new File(localDirectory, relativePath);
                // 确保父目录存在
                if (!localFile.getParentFile().exists() && !localFile.getParentFile().mkdirs()) {
                    System.err.println("Failed to create directory: " + localFile.getParent());
                    continue;
                }

                try {
                    // 强制覆盖已存在的文件
                    if (localFile.exists() && !localFile.delete()) {
                        System.err.println("Failed to delete existing file: " + localFile.getPath());
                        continue;
                    }
                    // 下载对象
                    downloadObject(client, bucketName, objectKey, localFile.getPath());
                } catch (SdkClientException e) {
                    if (e.getCause() instanceof FileAlreadyExistsException) {
                        System.err.println("File already exists (unexpected): " + localFile.getPath());
                    } else {
                        System.err.println("Failed to download " + objectKey + ": " + e.getMessage());
                    }
                }
            }

            // 更新分页 Token
            listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .continuationToken(listResponse.nextContinuationToken())
                    .maxKeys(1000)
                    .build();
        } while (listResponse.isTruncated());
    }

    public static void clearBucket(S3Client client, String bucketName) {
        try {
            // 构造列出对象的请求
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            ListObjectsV2Response listResponse;
            do {
                // 列出对象
                listResponse = client.listObjectsV2(listRequest);
                // 遍历所有对象，逐个删除
                for (S3Object s3Object : listResponse.contents()) {
                    DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Object.key())
                            .build();
                    client.deleteObject(deleteRequest);
                    System.out.println("Deleted object: " + s3Object.key());
                }
                // 如果结果被截断，则继续使用 ContinuationToken 进行分页查询
                listRequest = listRequest.toBuilder()
                        .continuationToken(listResponse.nextContinuationToken())
                        .build();
            } while (listResponse.isTruncated());
            System.out.println("Bucket " + bucketName + " is now empty.");
        } catch (AwsServiceException e) {
            System.err.println("Failed to clear bucket " + bucketName + ": " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    
    public static void main(String[] args) {
        // if (args.length != 3) {
        //     System.out.println("Usage: java -jar SeaweedFSDemoUpload.jar <bucket> <object-name> <input>");
        //     System.out.println("Usage: java -jar SeaweedFSDemoDownload.jar <bucket> <object-name> <output>");
        //     System.exit(1);
        // }
        long start_time = System.currentTimeMillis();
        // createBucket(client, "test-bucket3");
        // uploadObject(client, args[0], args[1], args[2]);
        // uploadObject(client, "test-bucket1", "tpc-ds-3G.zip", "/home/jiguoding/data/disk/tpc-ds-3G.zip");
        // downloadObject(client, args[0], args[1], args[2]);
        // deleteObject(client, "test-bucket1", "tpc-ds-3G.zip");
        // listBuckets(client);
        // clearBucket(client, "test-bucket2");
        listObjects(client, "test-bucket2");

        // 递归上传一整个目录，用法：java -jar SeaweedFSDemoDownload.jar <bucket-name> <directory>;
        // uploadDirectory(client, args[0], args[1]);
        // 下载所有前缀为prefix的对象,即下载一个目录,用法:java -jar seaweedfs-demo-download-jar-with-dependencies.jar <bucket-name> <prefix> <directory>
        // downloadDirectory(client, args[0], args[1], args[2]);
        long end_time = System.currentTimeMillis();
        System.out.println("Transferring time taken: " + (end_time - start_time) / 1e3 + " s");
    }
}
