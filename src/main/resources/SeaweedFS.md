# SeaweedFS

## docker部署（单节点）

```shell

sudo docker run -d -p 8333:8333 -p 9333:9333 -p 8080:8080 \
        -v /data1/jgd/data/seaweedfs/data:/data \
        -v /data1/jgd/data/seaweedfs/config:/etc/seaweedfs \
        --name seaweedfs \
        chrislusf/seaweedfs \
        server -master.port=9333 -volume.port=8080 -s3 -s3.port=8333 -s3.config=/etc/seaweedfs/s3_config.json
```

### 访问凭证

#### s3_config.json

```json
{
  "identities": [
    {
      "name": "admin",
      "credentials": [
        {
          "accessKey": "admin", 
          "secretKey": "Fixed0630"
        }
      ],
      "actions": ["Read", "Write", "List"]
    }
  ]
}
```
要注意的是 `accessKey` 和 `secretKey` 的写法，与s3标准配置里的 `s3_access_key_id` 和 `s3_secret_access_key` 的写法不一样。

## Java交互（S3 Api交互）

#### 0. 创建与 SeaweedFS 交互的客户端

```java
// 与SeaweedFS交互的客户端
private static S3Client client;

static {
    try {
        client = S3Client.builder()
                .endpointOverride(URI.create("http://Pasak8s-20:8333"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("admin", "Fixed0630")
                ))
                .region(Region.US_EAST_1) // SeaweedFS不强制区域校验
                .forcePathStyle(true) // 强制路径风格,避免DNS路径解析问题
                .build();
        System.out.println("SeaweedFS client created.");
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
```

#### 1. 创建一个新的桶
```java
public static void createBucket(S3Client client, String bucketName) {
    try {
        client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        System.out.println("Successfully created bucket: " + bucketName);
    } catch (AwsServiceException e) {
        throw new RuntimeException(e);
    }
}
```

#### 2. 向桶上传对象
```java
public static void uploadObject(S3Client client, String bucketName, String objectName, String input) {
    client.putObject(PutObjectRequest.builder().bucket(bucketName).key(objectName).build(), Paths.get(input));
    System.out.println("Successfully uploaded " + objectName + " to " + bucketName);
}
```

#### 3. 从桶中下载对象
```java
public static void downloadObject(S3Client client, String bucketName, String objectName, String output) {
    client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectName).build(), Paths.get(output));
    System.out.println("Successfully downloaded " + objectName + " from " + bucketName);
}
```

#### 4. 删除桶中对象
```java
public static void deleteObject(S3Client client, String bucketName, String objectName) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectName).build());
    System.out.println("Successfully deleted " + objectName + " from " + bucketName);
}
```

#### 5. 批量上传多个文件
```java

```

#### 上传一个文件（917MB）（机械硬盘）

Successfully uploaded tpc-ds-3G.zip to test-bucket1 Transferring time taken: 3.757 s  
Successfully uploaded tpc-ds-3G.zip to test-bucket1 Transferring time taken: 3.793 s  
Successfully uploaded tpc-ds-3G.zip to test-bucket1 Transferring time taken: 3.77 s

| real  | user  | sys   |
|-------|-------|-------|
| 4.227 | 5.718 | 1.351 |
| 4.275 | 5.873 | 1.259 |
| 4.259 | 5.816 | 1.284 |

利用了多核并行计算，因此 $real < user$

#### 下载一个文件（917MB）（机械硬盘）

Successfully downloaded tpc-ds-3G.zip from test-bucket1 Transferring time taken: 1.032 s  
Successfully downloaded tpc-ds-3G.zip from test-bucket1 Transferring time taken: 1.068 s  
Successfully downloaded tpc-ds-3G.zip from test-bucket1 Transferring time taken: 1.008 s

| real  | user  | sys   |
|-------|-------|-------|
| 1.505 | 1.774 | 0.886 |
| 1.552 | 1.887 | 0.864 |
| 1.510 | 1.910 | 0.834 |

#### 上传一个文件（917MB）（固态硬盘）

Successfully uploaded tpc-ds-3G.zip to test-bucket1 Transferring time taken: 3.756 s  
Successfully uploaded tpc-ds-3G.zip to test-bucket1 Transferring time taken: 3.779 s  
Successfully uploaded tpc-ds-3G.zip to test-bucket1 Transferring time taken: 3.801 s

| real  | user  | sys   |
|-------|-------|-------|
| 4.241 | 5.809 | 1.315 |
| 4.257 | 5.696 | 1.37  |
| 4.281 | 5.801 | 1.303 |

#### 下载一个文件（917MB）（固态硬盘）

Successfully downloaded tpc-ds-3G.zip from test-bucket1 Transferring time taken: 0.841 s  
Successfully downloaded tpc-ds-3G.zip from test-bucket1 Transferring time taken: 0.86 s  
Successfully downloaded tpc-ds-3G.zip from test-bucket1 Transferring time taken: 0.834 s

| real  | user  | sys   |
|-------|-------|-------|
| 1.320 | 1.879 | 0.62  |
| 1.33  | 1.776 | 0.665 |
| 1.304 | 1.825 | 0.642 |

#### 上传多个文件（）（机械硬盘）
