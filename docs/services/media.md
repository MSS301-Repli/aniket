# 03. Kiến trúc `media-service` (`com.animallocket.media`)

`media-service` (`:8103`, MySQL `media_db` + S3/Cloudflare R2) chịu trách nhiệm:
- Cấp vé upload trực tiếp lên kho lưu trữ đối tượng (`Presigned PUT` URL sống 5 phút) kèm kiểm tra hạn mức 60 ảnh/giờ.
- Xác nhận hoàn tất upload (`complete`), kiểm tra đối tượng thực tế trên bucket (`stat`), kiểm tra kích thước & `Content-Type`, chuyển trạng thái `READY` và phát sự kiện `PHOTO_READY` (hoặc `PHOTO_REJECTED`) qua Outbox.
- Cấp URL xem ảnh ngắn hạn (10 phút) sau khi xác minh người xem là chủ ảnh hoặc là bạn bè không bị chặn thông qua `UserClient`.
- Dọn dẹp định kỳ các job `PENDING_UPLOAD` bị bỏ rơi sau 1 giờ (`MediaJanitor`).

---

## 1. Class Diagram (`media-service`)

```mermaid
classDiagram
    direction TB

    %% ==========================================
    %% Domain Entities & Repositories
    %% ==========================================
    class MediaJob {
        <<@Entity media_jobs>>
        -String id
        -String ownerPublicId
        -Context context
        -String refId
        -String rawKey
        -String publicKey
        -Status status
        -String leadAnimal
        -String method
        -Integer facesDetected
        -String rejectReason
        -Instant createdAt
        -Instant updatedAt
        +touch() void
    }
    note for MediaJob "Theo dõi vòng đời ảnh:\n- Context: POST | CHAT\n- Status: PENDING_UPLOAD -> READY | REJECTED."

    class OwnerAnimal {
        <<@Entity owner_animals>>
        -String publicId
        -String leadAnimal
    }
    note for OwnerAnimal "Bản sao cục bộ con vật chủ đạo của người dùng\nđược đồng bộ từ sự kiện USER_REGISTERED và LEAD_ANIMAL_CHANGED."

    class MediaJobRepository {
        <<interface>>
        +findByStatusInAndUpdatedAtBefore(Collection~Status~ statuses, Instant before) List~MediaJob~
        +findByOwnerPublicId(String ownerPublicId) List~MediaJob~
        +countByOwnerPublicIdAndCreatedAtGreaterThanEqual(String ownerPublicId, Instant since) long
    }

    class OwnerAnimalRepository {
        <<interface>>
    }

    %% ==========================================
    %% Storage & S2S Client
    %% ==========================================
    class ObjectStorage {
        <<interface>>
        +presignUpload(String key, String contentType, Duration ttl) PresignedPut
        +presignDownload(String key, Duration ttl) String
        +stat(String key) Optional~ObjectInfo~
        +delete(String key) void
    }
    note for ObjectStorage "Giao diện trừu tượng hoá kho lưu trữ đối tượng S3 / Cloudflare R2."

    class S3ObjectStorage {
        <<@Component>>
        -MinioClient client
        -MinioClient signer
        -String bucket
        +presignUpload(String key, String contentType, Duration ttl) PresignedPut
        +presignDownload(String key, Duration ttl) String
        +stat(String key) Optional~ObjectInfo~
        +delete(String key) void
    }
    note for S3ObjectStorage "Sử dụng MinioClient tương thích S3/R2.\nCấp Presigned PUT kèm header Content-Type bắt buộc."

    class UserClient {
        <<@Component>>
        -RestClient client
        -CircuitBreakerFactory breakers
        +user(String publicId) Optional~InternalUser~
        +relationship(String a, String b) Relationship
    }
    note for UserClient "Gọi nội bộ sang user-service có bọc CircuitBreaker.\nFail-closed (503 USER_SERVICE_UNAVAILABLE) nếu không kiểm tra được quan hệ bạn bè."

    %% ==========================================
    %% Services, Janitor, Listener & Controller
    %% ==========================================
    class MediaService {
        <<@Service>>
        -MediaJobRepository jobs
        -OwnerAnimalRepository ownerAnimals
        -ObjectStorage storage
        -UserClient users
        -OutboxService outbox
        -long maxBytes
        -int maxUploadsPerHour
        +requestUpload(String owner, UploadRequest req) UploadTicket
        +complete(String owner, String mediaId) MediaStatus
        +status(String owner, String mediaId) MediaStatus
        +viewUrl(String viewer, String mediaId) ViewUrl
        +internal(String mediaId) InternalMedia
        +setOwnerAnimal(String publicId, String animal) void
        +onUserDeleted(String publicId) void
        +expireAbandoned() int
    }

    class MediaJanitor {
        <<@Component>>
        -MediaService media
        +expireAbandonedJobs() void
    }
    note for MediaJanitor "Quét mỗi 60 giây để xoá các job PENDING_UPLOAD quá 1 giờ và dọn file rác trên S3/R2."

    class MediaEventsListener {
        <<@Component>>
        -IdempotencyService idempotency
        -MediaService media
        +onMessage(String json) void
        ~handle(EventEnvelope e) void
    }

    class MediaController {
        <<@RestController>>
        -MediaService media
        +upload(String me, UploadRequest req) UploadTicket
        +complete(String me, String id) MediaStatus
        +status(String me, String id) MediaStatus
        +url(String me, String id) ViewUrl
        +internal(String id) InternalMedia
    }

    ObjectStorage <|.. S3ObjectStorage : implements
    MediaController --> MediaService : delegates
    MediaJanitor --> MediaService : schedules
    MediaEventsListener --> MediaService : updates/deletes
    MediaService --> MediaJobRepository : persists
    MediaService --> OwnerAnimalRepository : caches animal
    MediaService --> ObjectStorage : presigns & stats
    MediaService --> UserClient : verifies friendship
```

---

## 2. Sequence Diagrams theo Từng Class (`media-service`)

### 2.1 Class `MediaController` & `MediaService` — Xin vé Upload, Hoàn tất Upload & Cấp URL Xem ảnh

```mermaid
sequenceDiagram
    autonumber
    actor Client as Mobile Client
    participant MC as MediaController
    participant MS as MediaService
    participant Repo as MediaJobRepository (MySQL)
    participant S3 as ObjectStorage (S3 / R2)
    participant UC as UserClient (user-service)
    participant Outbox as OutboxService

    Note over Client,S3: 1. Xin cấp vé Upload (POST /api/v1/media/uploads)
    Client->>MC: POST /api/v1/media/uploads ({context, refId, contentType, sizeBytes})
    MC->>MS: requestUpload(me, req)
    MS->>MS: Kiểm tra contentType thuộc [jpeg, png, webp, heic] (400 UNSUPPORTED_MEDIA_TYPE)
    MS->>MS: Kiểm tra sizeBytes <= 10MB (400 FILE_TOO_LARGE)
    MS->>Repo: countByOwnerPublicIdAndCreatedAtGreaterThanEqual(me, now - 1h)
    alt Đã tạo >= 60 yêu cầu upload trong 1 giờ qua
        MS-->>Client: 429 Too Many Requests (UPLOAD_LIMIT)
    else Hợp lệ
        MS->>Repo: save(MediaJob(id=UUID, owner=me, status=PENDING_UPLOAD, rawKey=id+ext))
        MS->>S3: presignUpload(rawKey, contentType, 5 minutes)
        S3-->>MS: PresignedPut(uploadUrl, headers)
        MS-->>MC: UploadTicket(mediaId, uploadUrl, headers, 300)
        MC-->>Client: 201 Created (UploadTicket)
    end

    Note over Client,S3: 2. Client PUT trực tiếp file ảnh đã xoá EXIF lên S3/R2
    Client->>S3: PUT <uploadUrl> (headers: Content-Type, body: image bytes)
    S3-->>Client: 200 OK

    Note over Client,Outbox: 3. Báo hoàn tất Upload (POST /api/v1/media/{id}/complete)
    Client->>MC: POST /api/v1/media/{id}/complete
    MC->>MS: complete(me, mediaId)
    MS->>Repo: ownedJob(me, mediaId) (Nếu status != PENDING_UPLOAD trả ngay kết quả cũ - Idempotent)
    MS->>S3: stat(job.rawKey)
    alt Chưa có file trên S3/R2
        S3-->>MS: Optional.empty()
        MS-->>Client: 409 Conflict (NOT_UPLOADED)
    else File vượt quá 10MB hoặc sai Content-Type thực tế
        S3-->>MS: ObjectInfo (không hợp lệ)
        MS->>S3: delete(rawKey)
        MS->>Repo: job.setStatus(REJECTED), setRejectReason(...)
        MS->>Outbox: publish(MEDIA, PHOTO_REJECTED, mediaId, PhotoRejected)
        MS-->>Client: 200 OK MediaStatus(REJECTED)
    else File hợp lệ
        S3-->>MS: ObjectInfo (hợp lệ)
        MS->>Repo: job.setStatus(READY), publicKey=rawKey, method="NONE", facesDetected=0
        MS->>Outbox: publish(MEDIA, PHOTO_READY, mediaId, PhotoReady)
        MS-->>MC: MediaStatus(READY)
        MC-->>Client: 200 OK MediaStatus(READY)
    end

    Note over Client,UC: 4. Xin URL xem ảnh có kiểm tra quyền bạn bè (GET /api/v1/media/{id}/url)
    Client->>MC: GET /api/v1/media/{id}/url
    MC->>MS: viewUrl(viewer, mediaId)
    MS->>Repo: findById(mediaId) (Phải ở trạng thái READY, nếu không trả 404)
    opt viewer != job.ownerPublicId
        MS->>UC: relationship(viewer, job.ownerPublicId)
        UC-->>MS: Relationship(friends, blocked)
        alt !friends || blocked
            MS-->>Client: 404 Not Found (Không tiết lộ sự tồn tại của ảnh)
        end
    end
    MS->>S3: presignDownload(job.publicKey, 10 minutes)
    S3-->>MS: signedDownloadUrl
    MS-->>MC: ViewUrl(signedDownloadUrl, 600)
    MC-->>Client: 200 OK (ViewUrl)
```

---

### 2.2 Class `MediaJanitor` & `MediaEventsListener` — Dọn dẹp định kỳ & Đồng bộ Sự kiện

```mermaid
sequenceDiagram
    autonumber
    participant Sched as Spring Scheduler (60s)
    participant Janitor as MediaJanitor
    participant Kafka as Kafka (user.events)
    participant Listener as MediaEventsListener
    participant Idemp as IdempotencyService
    participant MS as MediaService
    participant Repo as MediaJob / OwnerAnimal Repos
    participant S3 as ObjectStorage

    Note over Sched,S3: 1. Dọn dẹp các job PENDING_UPLOAD bị bỏ rơi quá 1 giờ
    Sched->>Janitor: expireAbandonedJobs()
    Janitor->>MS: expireAbandoned()
    MS->>Repo: findByStatusInAndUpdatedAtBefore([PENDING_UPLOAD], now - 1h)
    loop Từng MediaJob bị bỏ rơi
        MS->>S3: delete(job.rawKey)
        MS->>Repo: jobs.delete(job)
    end

    Note over Kafka,S3: 2. Đồng bộ sự kiện từ user.events
    Kafka->>Listener: onMessage(json)
    Listener->>Idemp: firstTime(e.eventId())
    opt Sự kiện mới
        alt USER_REGISTERED hoặc LEAD_ANIMAL_CHANGED
            Listener->>MS: setOwnerAnimal(publicId, leadAnimal)
            MS->>Repo: ownerAnimals.save(new OwnerAnimal(publicId, leadAnimal))
        else USER_DELETED
            Listener->>MS: onUserDeleted(publicId)
            MS->>Repo: findByOwnerPublicId(publicId)
            loop Từng MediaJob của người dùng
                MS->>S3: delete(rawKey) & delete(publicKey)
                MS->>Repo: jobs.delete(job)
            end
            MS->>Repo: ownerAnimals.deleteById(publicId)
        end
    end
```

