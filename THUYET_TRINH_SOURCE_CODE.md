# Thuyet Trinh Source Code Website Quan Ly Phong Tro

## 1. Tong quan du an

Day la website quan ly phong tro ten SmartRent / Glass Living, duoc xay dung theo mo hinh web full-stack mot ung dung Spring Boot. He thong phuc vu hai nhom nguoi dung chinh:

- Khach thue: xem danh sach phong, xem chi tiet phong, dat phong, xem hop dong, hoa don, thanh toan, quan ly ho so ca nhan.
- Chu tro / quan tri vien: quan ly nha tro, phong, khach thue, hop dong, hoa don, bao tri, dien nuoc, bao cao, tu dong hoa va AI assistant.

Ung dung khong tach rieng frontend SPA va backend API thanh hai project doc lap. Frontend nam chung trong project Spring Boot, duoc render bang Thymeleaf o phia server va ket hop voi CSS/JS tinh.

## 2. Cong nghe su dung

### Backend

- Ngon ngu lap trinh: Java 21.
- Framework chinh: Spring Boot 3.3.5.
- Web MVC: Spring Web MVC.
- Template engine: Thymeleaf.
- Security: Spring Security.
- Xac thuc API: JWT voi thu vien `jjwt`.
- Xac thuc web: session cookie + form login.
- ORM / database access: Spring Data JPA + Hibernate.
- Migration database: Flyway.
- Validation: Spring Boot Starter Validation.
- Build tool: Maven Wrapper (`mvnw`, `mvnw.cmd`).
- Sinh code boilerplate: Lombok.
- API documentation: Springdoc OpenAPI / Swagger UI.
- Monitoring: Spring Boot Actuator + Prometheus metrics.
- Gui mail: Spring Boot Mail.
- Export Excel: Apache POI.
- Tao PDF: OpenPDF.
- Luu tru file/S3 config: MinIO SDK, hien tai co local upload service.
- Ma hoa du lieu nhay cam: Jasypt.

### Frontend

- Template HTML: Thymeleaf templates.
- CSS framework: Tailwind CSS 3.4.
- CSS build pipeline: Node.js + npm + Tailwind CLI + PostCSS + Autoprefixer.
- JavaScript: Vanilla JavaScript module nho trong `src/main/resources/static/js`.
- UI interaction: HTMX / Alpine style duoc dung trong template va static assets.
- Icon / style: giao dien co thiet ke glass style, token nam trong Tailwind config va CSS input.

### Database va ha tang local

- Database dev: PostgreSQL local, cau hinh trong `src/main/resources/application-dev.yml`.
- Database prod: PostgreSQL qua bien moi truong, cau hinh trong `src/main/resources/application-prod.yml`.
- Redis: co cau hinh, dung cho profile production/cache/health tuy theo moi truong.
- Docker Compose: co file `docker-compose.yml` de chay cac dich vu phu tro local.
- Upload file: thu muc `uploads/`, phuc vu qua endpoint `/uploads/**`.

## 3. Backend nam o dau?

Backend nam trong:

```text
src/main/java/vn/glassliving/
```

Day la package goc cua ung dung Java. File khoi dong chinh:

```text
src/main/java/vn/glassliving/GlassLivingApplication.java
```

Mot so nhom backend quan trong:

```text
src/main/java/vn/glassliving/config/
```

Chua cau hinh he thong nhu security, MVC, data khoi tao dev.

```text
src/main/java/vn/glassliving/common/
```

Chua code dung chung: response DTO, exception handler, audit entity, helper web, upload service, formatter, slug utility.

```text
src/main/java/vn/glassliving/auth/
```

Chua dang ky, dang nhap, user entity, JWT, security user details.

```text
src/main/java/vn/glassliving/room/
src/main/java/vn/glassliving/property/
```

Chua nghiep vu nha tro, phong tro, tien ich, hinh anh phong.

```text
src/main/java/vn/glassliving/booking/
src/main/java/vn/glassliving/contract/
src/main/java/vn/glassliving/invoice/
src/main/java/vn/glassliving/payment/
```

Chua nghiep vu dat phong, hop dong, hoa don, thanh toan.

```text
src/main/java/vn/glassliving/admin/page/
```

Chua cac controller phia admin: dashboard, rooms, tenants, invoices, contracts, utilities, reports, automations, AI.

```text
src/main/java/vn/glassliving/customer/controller/
```

Chua controller khu vuc khach thue: dashboard, profile, favorites, invoices, contracts, booking.

```text
src/main/java/vn/glassliving/ai/
```

Chua API va service AI assistant.

```text
src/main/java/vn/glassliving/utility/
```

Chua nghiep vu dien nuoc, tinh tien phong, export Excel.

Trong source hien tai co 105 file Java.

## 4. Frontend nam o dau?

Frontend nam chu yeu trong:

```text
src/main/resources/templates/
src/main/resources/static/
```

### Templates Thymeleaf

```text
src/main/resources/templates/layouts/
```

Chua layout chinh cho tung nhom trang:

- `admin-layout.html`: layout khu vuc admin.
- `customer-layout.html`: layout khu vuc khach thue / public.
- `auth-layout.html`: layout dang nhap, dang ky.

```text
src/main/resources/templates/fragments/
```

Chua cac thanh phan dung lai nhieu noi: navbar, footer, flash message, pagination, room card, notification list, AI button.

```text
src/main/resources/templates/customer/
```

Chua giao dien phia khach hang:

- `home.html`: trang chu / danh sach phong.
- `room-detail.html`: chi tiet phong.
- `booking.html`: luong dat phong.
- `dashboard.html`: dashboard khach thue.
- `profile.html`: ho so ca nhan.
- `invoices.html`, `invoice-detail.html`: hoa don.
- `favorites.html`: phong yeu thich.
- `notifications.html`: thong bao.
- `payment-checkout.html`: trang thanh toan.

```text
src/main/resources/templates/admin/
```

Chua giao dien phia quan tri:

- `dashboard.html`: tong quan.
- `properties.html`: quan ly co so/nha tro.
- `rooms.html`: quan ly phong.
- `tenants.html`, `tenant-detail.html`: quan ly khach thue.
- `contracts.html`, `contract-detail.html`: quan ly hop dong.
- `invoices.html`, `invoice-detail.html`: quan ly hoa don.
- `maintenance.html`: bao tri.
- `utilities.html`, `utility-room-detail.html`: dien nuoc.
- `reports.html`: bao cao.
- `automations.html`: tu dong hoa.
- `ai.html`: AI assistant cho admin.

```text
src/main/resources/templates/auth/
```

Chua giao dien dang nhap, dang ky, quen mat khau.

### Static assets

```text
src/main/resources/static/css/input.css
```

File CSS dau vao, viet custom CSS va khai bao cac class Tailwind.

```text
src/main/resources/static/css/app.css
```

File CSS da build tu Tailwind. Website can file nay de hien thi giao dien dung.

```text
src/main/resources/static/js/app.js
src/main/resources/static/js/modules/
```

Chua JavaScript client:

- `app.js`: logic frontend dung chung.
- `modules/favorite.js`: xu ly yeu thich phong.
- `modules/ai-assistant.js`: xu ly AI assistant popup / request API.

```text
src/main/resources/static/img/favicon.svg
```

Icon cua website.

## 5. Cau hinh quan trong nam o dau?

```text
pom.xml
```

Cau hinh backend Java, Spring Boot, dependency Maven, plugin build JAR.

```text
package.json
```

Cau hinh build frontend CSS bang Tailwind:

```text
npm run build:css
npm run watch:css
```

```text
tailwind.config.js
postcss.config.js
```

Cau hinh Tailwind CSS va PostCSS.

```text
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
```

Cau hinh Spring Boot:

- Port server mac dinh: 8080.
- Thymeleaf template.
- JPA/Hibernate.
- Flyway migration.
- Redis.
- Mail.
- JWT.
- Storage.
- AI.
- Payment config.

```text
src/main/resources/db/migration/
```

Chua cac file Flyway migration tao/cap nhat database.

```text
src/main/resources/db/dev-seed/
```

Chua seed data cho moi truong dev.

```text
docker-compose.yml
```

Chay cac dich vu phu tro local nhu PostgreSQL/Redis/MinIO/MailHog neu can.

## 6. Cach backend va frontend ket noi voi nhau

Frontend khong goi den mot backend rieng biet o project khac. Spring Boot vua render giao dien, vua xu ly logic backend.

Luon du lieu co ban:

1. Nguoi dung mo URL tren trinh duyet, vi du `/admin/rooms`.
2. Request vao Spring MVC Controller trong `src/main/java/vn/glassliving/...`.
3. Controller goi Service de xu ly nghiep vu.
4. Service goi Repository de doc/ghi database.
5. Controller dua du lieu vao Model.
6. Thymeleaf render file HTML trong `src/main/resources/templates/...`.
7. Trinh duyet nhan HTML + CSS + JS tu `src/main/resources/static/...`.

Vi du:

```text
/admin/rooms
-> RoomsPageController / RoomsActionController
-> RoomAdminService / RoomService
-> RoomRepository / RoomImageRepository / AmenityRepository
-> templates/admin/rooms.html
```

## 7. Mo hinh package backend

Du an to chuc theo module nghiep vu:

```text
auth          Dang ky, dang nhap, User, JWT, security user details
property      Quan ly co so/nha tro
room          Quan ly phong, anh phong, tien nghi
booking       Dat phong
contract      Hop dong
invoice       Hoa don
payment       Thanh toan
maintenance   Phieu bao tri/sua chua
notification  Thong bao
favorite      Phong yeu thich
review        Danh gia
utility       Dien nuoc, tinh tien phong, export Excel
report        Bao cao admin
automation    Tu dong hoa nhac hop dong/email
ai            AI assistant
admin         Cac trang controller cho admin
customer      Cac trang controller cho khach thue
common        Code dung chung
config        Cau hinh he thong
```

Trong moi module thuong co cac lop:

- `controller`: nhan request HTTP.
- `service`: xu ly nghiep vu.
- `repository`: lam viec voi database qua Spring Data JPA.
- `entity`: mapping bang database.
- `dto`: doi tuong truyen du lieu/form.

## 8. Bao mat va phan quyen

Bao mat nam chu yeu trong:

```text
src/main/java/vn/glassliving/config/SecurityConfig.java
src/main/java/vn/glassliving/auth/security/
```

Cac diem chinh:

- Web dung session cookie va form login.
- API co JWT bearer va session fallback cho request same-origin.
- Mat khau duoc hash bang BCrypt.
- Cac trang `/admin/**` chi danh cho OWNER/ADMIN tuy cau hinh.
- Cac trang `/me/**` danh cho nguoi dung da dang nhap.
- CSRF duoc dung cho form web.
- Static assets duoc public, upload `/uploads/**` yeu cau authenticated.

## 9. Database va migration

Du lieu duoc quan ly bang PostgreSQL trong moi truong dev/prod. Hibernate/JPA mapping cac class entity thanh bang database.

Migration nam trong:

```text
src/main/resources/db/migration/
```

Mot so nhom bang chinh:

- User / tai khoan.
- Property / co so nha tro.
- Room / phong.
- Amenity / tien nghi.
- Booking / dat phong.
- Contract / hop dong.
- Invoice / hoa don.
- Payment / thanh toan.
- MaintenanceTicket / bao tri.
- Notification / thong bao.
- UtilityReading / chi so dien nuoc.
- AutomationSetting / cau hinh tu dong hoa.
- AiQuery / lich su hoi AI.

Seed data dev nam trong:

```text
src/main/resources/db/dev-seed/V900__dev_sample_data.sql
```

## 10. Upload file va tai lieu khach thue

Thu muc upload local:

```text
uploads/
```

Code xu ly upload:

```text
src/main/java/vn/glassliving/common/storage/LocalUploadService.java
src/main/java/vn/glassliving/config/WebMvcConfig.java
```

Upload duoc dung cho anh CCCD/giay to khach thue. Ung dung phuc vu file qua duong dan:

```text
/uploads/**
```

## 11. Cac trang / endpoint nen gioi thieu khi thuyet trinh

Public:

```text
/
/rooms/{slug}
/login
/register
```

Customer:

```text
/me
/me/profile
/me/invoices
/me/favorites
/me/notifications
/booking/start
```

Admin:

```text
/admin
/admin/properties
/admin/rooms
/admin/tenants
/admin/contracts
/admin/invoices
/admin/maintenance
/admin/utilities
/admin/reports
/admin/automations
/admin/ai
```

API:

```text
/api/v1/auth
/api/v1/rooms
/api/v1/bookings
/api/v1/favorites
/api/v1/me/notifications
/api/v1/ai/chat
```

He thong:

```text
/actuator/health
/swagger-ui.html
/v3/api-docs
```

## 12. Cach chay du an local

Neu dung script co san tren Windows:

```powershell
.\start-dev.ps1
```

Hoac chay thu cong:

```powershell
npm install
npm run build:css
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Build JAR:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Sau khi chay, mo:

```text
http://localhost:8080
```

## 13. Tai khoan demo

Mat khau chung:

```text
password123
```

Tai khoan demo:

```text
owner@glass.living   Chu tro / owner
tenant@glass.living  Khach thue
admin@glass.living   Quan tri vien
```

## 14. Diem nen nhan manh khi thuyet trinh

1. Du an la mot ung dung Spring Boot full-stack, frontend va backend nam chung trong mot project.
2. Backend duoc chia module theo nghiep vu, de doc va de bao tri.
3. Frontend dung Thymeleaf render server-side, nen SEO va load trang ban dau tot.
4. Tailwind CSS duoc build rieng qua npm, nhung file CSS output nam trong static resource cua Spring.
5. Database duoc quan ly bang Flyway migration, giup version schema ro rang.
6. Bao mat dung Spring Security, co session login cho web va JWT cho API.
7. He thong co day du module cua bai toan quan ly phong tro: phong, khach thue, hop dong, hoa don, dien nuoc, bao tri, bao cao.
8. Ung dung co cau hinh dev/prod rieng, co Maven Wrapper de build ma khong can cai Maven rieng.

## 15. Tom tat mot cau de noi trong slide

Website quan ly phong tro nay duoc xay dung bang Java 21 va Spring Boot 3.3 o backend, Thymeleaf + Tailwind CSS + JavaScript o frontend, su dung PostgreSQL va Flyway cho database, to chuc source theo module nghiep vu trong `src/main/java/vn/glassliving`, con giao dien nam trong `src/main/resources/templates` va `src/main/resources/static`.
