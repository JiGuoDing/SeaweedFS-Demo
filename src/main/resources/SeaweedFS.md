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

#### 上传多个文件（2000个8.2MB的seis文件）（固态硬盘）（4个线程并行）

Transferring time taken: 26.825 s
Transferring time taken: 26.302 s
Transferring time taken: 26.194 s

| real   | user    | sys    |
|--------|---------|--------|
| 27.332 | 1m0.007 | 18.369 |
| 26.786 | 58.580  | 17.223 |
| 26.676 | 59.282  | 17.129 |

#### 上传多个文件（2000个8.2MB的seis文件）（机械硬盘）

Transferring time taken: 25.541 s
Transferring time taken: 26.872 s
Transferring time taken: 26.152 s

| real   | user   | sys    |
|--------|--------|--------|
| 26.041 | 58.526 | 15.799 |
| 27.348 | 58.693 | 16.826 |
| 26.637 | 58.985 | 15.972 |

#### 下载多个文件（2000个8.2MB的seis文件）（固态硬盘）

Transferring time taken: 18.349 s
Transferring time taken: 18.717 s
Transferring time taken: 18.644 s

| real   | user   | sys   |
|--------|--------|-------|
| 18.828 | 10.990 | 9.402 |
| 19.192 | 10.508 | 9.438 |
| 19.135 | 10.970 | 9.564 |

#### 下载多个文件（2000个8.2MB的seis文件）（机械硬盘）

Transferring time taken: 20.422 s
Transferring time taken: 20.148 s
Transferring time taken: 20.182 s

| real   | user   | sys    |
|--------|--------|--------|
| 20.910 | 10.974 | 12.867 |
| 20.635 | 10.886 | 12.693 |
| 20.664 | 10.934 | 12.857 |

#### 上传多个文件（5000个11MB的seis文件）（固态硬盘）

Transferring time taken: 75.095 s
Transferring time taken: 76.325 s
Transferring time taken: 76.325 s

| real   | user    | sys    |
|--------|---------|--------|
| 75.615 | 183.319 | 49.116 |
| 76.822 | 181.780 | 52.388 |
| 76.855 | 182.577 | 49.816 |

#### 上传多个文件（5000个11MB的seis文件）（机械硬盘）

Transferring time taken: 233.943 s
Transferring time taken: 77.661 s
Transferring time taken: 74.007 s

| real    | user    | sys    |
|---------|---------|--------|
| 234.516 | 179.547 | 52.607 |
| 78.547  | 182.414 | 49.349 |
| 74.537  | 183.359 | 48.863 |

#### 下载多个文件（5000个11MB的seis文件）（固态硬盘）

Transferring time taken: 59.321 s
Transferring time taken: 60.438 s
Transferring time taken: 59.875 s

| real   | user   | sys    |
|--------|--------|--------|
| 59.745 | 28.032 | 29.992 |
| 60.769 | 26.517 | 30.044 |
| 60.161 | 27.051 | 30.313 |

#### 下载多个文件（5000个11MB的seis文件）（机械硬盘）

Transferring time taken: 67.239 s
Transferring time taken: 65.234 s
Transferring time taken: 65.629 s

| real   | user   | sys    |
|--------|--------|--------|
| 67.742 | 25.875 | 39.633 |
| 65.731 | 25.186 | 39.360 |
| 66.115 | 25.777 | 39.669 |
