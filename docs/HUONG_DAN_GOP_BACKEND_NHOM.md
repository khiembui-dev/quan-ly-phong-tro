# Hướng dẫn gộp backend của nhóm vào source hiện tại

Tài liệu này dùng cho 3 bạn: **Mẫn**, **Huy Khang**, **Huy**.

Source hiện tại là source đã được xóa backend của 3 bạn, chỉ giữ toàn bộ frontend, phần backend của anh, AI và thanh toán. Khi các bạn làm xong phần của mình ở **source gốc**, hãy copy đúng phần được phân công sang **source hiện tại** theo hướng dẫn dưới đây.

## Quy ước

- **Source gốc**: bản backup/full source trước khi xóa backend của 3 bạn.
- **Source hiện tại**: thư mục đang dùng để up GitHub sau khi đã xóa backend của 3 bạn.
- Chỉ copy các file backend được giao. Không copy toàn bộ `src/`, không copy toàn bộ project.
- Không copy các thư mục sinh tự động: `node_modules/`, `target/`, `logs/`, `uploads/`, `.git/`.
- Frontend đã có sẵn trong source hiện tại, gồm `templates`, `static/js`, `static/css`. Chỉ copy template/js nếu bạn thật sự có sửa frontend ở source gốc và đã so sánh kỹ.

## Cách làm chung trước khi copy

1. Mở song song 2 thư mục:
   - Source gốc.
   - Source hiện tại.

2. Trước khi copy, kiểm tra source hiện tại còn build được:

   ```powershell
   .\mvnw.cmd -q test
   ```

3. Copy đúng thư mục/file của mình theo danh sách bên dưới.

4. Sau khi copy xong, chạy lại:

   ```powershell
   .\mvnw.cmd -q test
   ```

5. Nếu lỗi compile, đọc lỗi để biết thiếu import, thiếu field trong controller, hoặc đã copy thiếu service/repository/entity.

## Lưu ý rất quan trọng về file dùng chung

Một số route customer không nằm trong controller riêng mà nằm chung trong:

```text
src/main/java/vn/glassliving/customer/controller/CustomerWebController.java
```

Không được copy đè nguyên file này từ source gốc sang source hiện tại, vì file gốc chứa lẫn phần của nhiều người. Nếu copy đè nguyên file, bạn sẽ vô tình đưa cả phần của người khác vào source.

Với file này, chỉ merge đúng method/field/import được nêu trong từng phần bên dưới.

## Phần của Mẫn

Mẫn phụ trách:

- `/admin/utilities`
- `/admin/tickets`
- `/customer/notifications`

### 1. Copy backend admin utilities

Copy nguyên thư mục này từ source gốc sang source hiện tại:

```text
src/main/java/vn/glassliving/admin/page/utilities/
```

Nếu trong source gốc Mẫn có sửa logic service/repository/entity của utilities, kiểm tra và copy thêm các file liên quan trong:

```text
src/main/java/vn/glassliving/utility/
```

Lưu ý: source hiện tại vẫn đang giữ package `utility` vì AI/report có thể phụ thuộc. Vì vậy nếu copy, nên so sánh file trước, tránh ghi đè nhầm thay đổi của người khác.

### 2. Copy backend admin tickets

Copy nguyên thư mục này:

```text
src/main/java/vn/glassliving/admin/page/tickets/
```

Nếu có sửa nghiệp vụ ticket, kiểm tra và copy thêm các file liên quan trong:

```text
src/main/java/vn/glassliving/maintenance/
```

Package `maintenance` hiện vẫn còn trong source hiện tại vì AI, notification và dashboard badge có dùng. Không xóa package này.

### 3. Copy backend customer notifications

Copy nguyên thư mục controller này:

```text
src/main/java/vn/glassliving/notification/controller/
```

Nếu có sửa service/repository/entity notification, kiểm tra và copy thêm:

```text
src/main/java/vn/glassliving/notification/entity/
src/main/java/vn/glassliving/notification/repository/
src/main/java/vn/glassliving/notification/service/
```

### 4. Merge route gửi ticket của customer

Trang `customer/notifications.html` có form gửi ticket và trả lời ticket. Các endpoint này trước đây nằm trong `CustomerWebController.java`, không nằm trong controller riêng.

Mẫn cần mở file ở source gốc:

```text
src/main/java/vn/glassliving/customer/controller/CustomerWebController.java
```

Sau đó chỉ copy/merge các phần sau sang file cùng tên ở source hiện tại:

- Method `createTicket(...)`
- Method `replyTicket(...)`
- Method `storeTicketAttachments(...)`
- Method `splitTicketPhotos(...)`

Cần kiểm tra thêm trong `CustomerWebController.java` hiện tại có đủ dependency sau chưa:

```java
private final MaintenanceService maintenanceService;
```

Các dependency khác như `RoomRepository`, `LocalUploadService`, `NotificationService` đã có sẵn hoặc có thể đã có sẵn trong file hiện tại. Nếu compile báo thiếu import, thêm đúng import từ source gốc, thường là:

```java
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.service.MaintenanceService;
```

Không copy nguyên `CustomerWebController.java`.

### 5. Test của Mẫn

Nếu Mẫn có test, copy lại các test tương ứng:

```text
src/test/java/vn/glassliving/maintenance/
```

Sau đó chạy:

```powershell
.\mvnw.cmd -q test
```

## Phần của Huy Khang

Huy Khang phụ trách:

- `/admin/invoices`
- `/admin/contracts`
- `/customer/booking`

### 1. Copy backend admin invoices

Copy nguyên thư mục:

```text
src/main/java/vn/glassliving/admin/page/invoices/
```

Nếu có sửa nghiệp vụ hóa đơn, kiểm tra và copy thêm file liên quan trong:

```text
src/main/java/vn/glassliving/invoice/
```

Lưu ý: source hiện tại vẫn giữ `invoice` vì phần thanh toán đang phụ thuộc trực tiếp vào `Invoice`, `InvoiceRepository`, `InvoiceService`. Không xóa package này.

### 2. Copy backend admin contracts

Copy nguyên thư mục:

```text
src/main/java/vn/glassliving/admin/page/contracts/
```

Nếu có sửa nghiệp vụ hợp đồng, kiểm tra và copy thêm file liên quan trong:

```text
src/main/java/vn/glassliving/contract/
```

Lưu ý: source hiện tại vẫn giữ `contract` vì phần thanh toán và phòng có liên kết hợp đồng.

### 3. Copy backend booking API/domain

Copy nguyên thư mục:

```text
src/main/java/vn/glassliving/booking/
```

Thư mục này thường gồm:

```text
src/main/java/vn/glassliving/booking/controller/
src/main/java/vn/glassliving/booking/dto/
src/main/java/vn/glassliving/booking/entity/
src/main/java/vn/glassliving/booking/repository/
src/main/java/vn/glassliving/booking/service/
```

### 4. Merge route `/customer/booking`

Route hiển thị trang booking trước đây nằm trong `CustomerWebController.java`, không nằm trong package `booking`.

Huy Khang cần mở file ở source gốc:

```text
src/main/java/vn/glassliving/customer/controller/CustomerWebController.java
```

Chỉ copy/merge các method sau sang file cùng tên ở source hiện tại:

- Method `bookingCatalog(...)`
- Method `bookingSuccess(...)`

Cần kiểm tra thêm các field sau trong `CustomerWebController.java` hiện tại:

```java
private final RoomService roomService;
private final PropertyRepository propertyRepository;
```

Nếu compile báo thiếu import, thêm import từ source gốc, thường là:

```java
import org.springframework.data.domain.Sort;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.service.RoomService;

import java.util.stream.Collectors;
```

Không copy nguyên `CustomerWebController.java`.

### 5. Test của Huy Khang

Copy lại test liên quan nếu có:

```text
src/test/java/vn/glassliving/booking/
src/test/java/vn/glassliving/admin/page/invoices/
```

Sau đó chạy:

```powershell
.\mvnw.cmd -q test
```

## Phần của Huy

Huy phụ trách:

- `/admin/tenants`
- `/admin/system-settings`
- `/customer/dashboard#rooms`

### 1. Copy backend admin tenants

Copy nguyên thư mục:

```text
src/main/java/vn/glassliving/admin/page/tenants/
```

Nếu có sửa nghiệp vụ user/tenant, kiểm tra kỹ trước khi copy các file trong:

```text
src/main/java/vn/glassliving/auth/
src/main/java/vn/glassliving/customer/
```

Không copy đè toàn bộ `auth` hoặc `customer` nếu không cần, vì đây là vùng dùng chung.

### 2. Copy backend system settings

Route `/admin/system-settings` nằm trong controller automation. Copy nguyên thư mục:

```text
src/main/java/vn/glassliving/admin/page/automations/
```

Nếu có sửa entity/repository/service automation, kiểm tra và copy thêm:

```text
src/main/java/vn/glassliving/automation/
```

Package `automation` hiện vẫn có trong source hiện tại vì một số màn hình và helper dùng thông tin liên hệ/chăm sóc khách.

### 3. Merge route `/customer/dashboard#rooms`

Phần này không có controller riêng. Route thật là:

```text
/customer/dashboard
```

Anchor `#rooms` chỉ là vị trí trong HTML. Backend cần method dashboard trong:

```text
src/main/java/vn/glassliving/customer/controller/CustomerWebController.java
```

Huy cần mở file `CustomerWebController.java` ở source gốc và chỉ copy/merge các phần sau:

- Method `dashboard(...)`
- Method `contactCards(...)`
- Method `latestUtilityHistory(...)`
- Method `compareUtilityReadingDesc(...)`
- Method `value(...)`
- Method `zaloUrl(...)`
- Method `phoneUrl(...)`
- Method `contactCard(...)`
- Method `firstNonBlank(...)`
- Record `ContactCard`

Cần kiểm tra thêm các field sau trong `CustomerWebController.java` hiện tại:

```java
private final MaintenanceTicketRepository ticketRepository;
private final AutomationSettingRepository automationSettingRepository;
private final PropertyRepository propertyRepository;
private final ContractRepository contractRepository;
private final UtilityReadingRepository utilityReadingRepository;

@Value("${app.customer-support.phone:}")
private String supportPhone;

@Value("${app.customer-support.email:hotro@smartrent.vn}")
private String supportEmail;

@Value("${app.customer-support.zalo:}")
private String supportZalo;
```

Nếu compile báo thiếu import, thêm đúng import từ source gốc, thường là:

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.utility.entity.UtilityReading;
import vn.glassliving.utility.repository.UtilityReadingRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
```

Không copy nguyên `CustomerWebController.java`, vì file gốc có cả phần ticket của Mẫn và booking của Huy Khang.

### 4. Test của Huy

Copy lại test liên quan nếu có:

```text
src/test/java/vn/glassliving/admin/page/tenants/
src/test/java/vn/glassliving/customer/controller/CustomerDashboardTemplateTest.java
```

Sau đó chạy:

```powershell
.\mvnw.cmd -q test
```

## Các file frontend đã có sẵn, không cần copy lại

Các file HTML/JS dưới đây vẫn đang còn trong source hiện tại. Chỉ copy từ source gốc nếu bạn có sửa frontend và đã so sánh kỹ:

```text
src/main/resources/templates/admin/utilities.html
src/main/resources/templates/admin/utility-room-detail.html
src/main/resources/templates/admin/maintenance.html
src/main/resources/templates/admin/invoices.html
src/main/resources/templates/admin/invoice-detail.html
src/main/resources/templates/admin/contracts.html
src/main/resources/templates/admin/contract-detail.html
src/main/resources/templates/admin/tenants.html
src/main/resources/templates/admin/tenant-detail.html
src/main/resources/templates/admin/automations.html
src/main/resources/templates/customer/notifications.html
src/main/resources/templates/customer/booking.html
src/main/resources/templates/customer/booking-success.html
src/main/resources/templates/customer/dashboard.html
src/main/resources/static/js/admin-utilities.js
src/main/resources/static/js/admin-tickets.js
src/main/resources/static/js/admin-contracts.js
src/main/resources/static/js/admin-tenants.js
src/main/resources/static/js/admin-notifications.js
src/main/resources/static/js/customer-portal.js
src/main/resources/static/js/modules/customer-ticket.js
```

## Cách copy bằng PowerShell

Ví dụ nếu source gốc nằm ở:

```text
D:\Downloads\smartrent-backup
```

và source hiện tại nằm ở:

```text
D:\Downloads\smartrent-deploy
```

Thì copy một thư mục backend như sau:

```powershell
$old = "D:\Downloads\smartrent-backup"
$new = "D:\Downloads\smartrent-deploy"

Copy-Item `
  -LiteralPath "$old\src\main\java\vn\glassliving\admin\page\utilities" `
  -Destination "$new\src\main\java\vn\glassliving\admin\page\utilities" `
  -Recurse `
  -Force
```

Với các phần nằm trong `CustomerWebController.java`, không dùng `Copy-Item` để copy đè. Mở 2 file và merge thủ công đúng method được giao.

## Checklist trước khi báo đã gộp xong

Mỗi bạn tự kiểm tra:

- Đã copy đúng thư mục backend của mình.
- Không copy đè toàn bộ `CustomerWebController.java`.
- Không copy `node_modules/`, `target/`, `logs/`, `uploads/`.
- Chạy được:

  ```powershell
  .\mvnw.cmd -q test
  ```

- Mở lại trang mình phụ trách và kiểm tra không còn lỗi 404/500.

## Thứ tự gộp đề xuất

Có thể gộp độc lập, nhưng nếu muốn ít xung đột hơn thì nên theo thứ tự:

1. Mẫn gộp utilities, tickets, notifications.
2. Huy Khang gộp invoices, contracts, booking.
3. Huy gộp tenants, system settings, dashboard rooms.

Lý do: `CustomerWebController.java` là file dễ bị conflict nhất. Người gộp sau cần kiểm tra lại các method đã có trước khi thêm phần của mình.
