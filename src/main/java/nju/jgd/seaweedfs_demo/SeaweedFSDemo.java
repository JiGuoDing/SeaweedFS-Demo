package nju.jgd.seaweedfs_demo;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用Amazon的s3 SDK与SeaweedFS交互
 */
public class SeaweedFSDemo {

    // 使用多线程并行上传一个目录中的多个文件，提高性能
    private static final Integer THREAD_COUNT = new Integer(4);

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
        for (S3Object content : listObjectsV2Response.contents()) {
            System.out.println(content.key());
        }
    }

    // 批量上传一个目录下的多个文件（递归，包含子目录中的文件）到SeaweedFS的某个桶中
    public static void uploadDirectory(S3Client client, String bucketName, String directory) {
        // 要上传的目录
        File baseDir = new File(directory);
        if (!baseDir.exists() || !baseDir.isDirectory()){
            System.out.println("Directory " + directory + " does not exist or is not a directory.");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        executor.shutdown();
    }

    public static void uploadRecursive(S3Client client, String bucketName, String directory, ExecutorService executor) {
        File baseDir = new File(directory);
        for (File file : baseDir.listFiles()) {
            if (file.isFile()) {
                // 构建SeaweedFS中的存储路径，保持目录结构
                String seaweedfsPath = directory + "/" + file.getName();
                // 将该文件上传到SeaweedFS中
                executor.submit(() -> uploadObject(client, bucketName, file.getName(), seaweedfsPath));
            }
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java -jar SeaweedFSDemoUpload.jar <bucket> <object-name> <input>");
            System.out.println("Usage: java -jar SeaweedFSDemoDownload.jar <bucket> <object-name> <output>");
            System.exit(1);
        }
        long start_time = System.currentTimeMillis();
        createBucket(client, "test-bucket3");
        // uploadObject(client, args[0], args[1], args[2]);
        // uploadObject(client, "test-bucket1", "tpc-ds-3G.zip", "/home/jiguoding/data/disk/tpc-ds-3G.zip");
        // downloadObject(client, args[0], args[1], args[2]);
        // deleteObject(client, "test-bucket1", "tpc-ds-3G.zip");
        // listBuckets(client);
        // listObjects(client, "test-bucket1");
        long end_time = System.currentTimeMillis();
        System.out.println("Transferring time taken: " + (end_time - start_time) / 1e3 + " s");
    }
}
