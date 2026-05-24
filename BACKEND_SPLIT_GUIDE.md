# Huong Dan Chia Backend Theo Nhom

Branch `frontend-base` la branch nen de upload len GitHub truoc.

Branch nay giu frontend, template, static asset va cau hinh du an, nhung khong chua backend Java va database migration that.

## Cau Truc Frontend Dang Co

- `src/main/resources/templates/`: giao dien Thymeleaf.
- `src/main/resources/static/`: CSS, JavaScript, hinh anh.
- `src/main/resources/messages*.properties`: ngon ngu hien thi.
- `package.json`, `tailwind.config.js`, `postcss.config.js`: build CSS frontend.
- `pom.xml`, `mvnw`, `mvnw.cmd`: khung Maven de nhom code lai backend Spring Boot.

## Branch Chia Viec

Moi nguoi nen tao backend tren branch rieng, xuat phat tu `frontend-base`.

| Nguoi | Branch | Trang phu trach |
|---|---|---|
| Anh | `feature/admin-properties-rooms` | `/admin/properties`, `/admin/rooms` |
| Thanh vien 2 | `feature/admin-invoices-contracts` | `/admin/invoices`, `/admin/contracts` |
| Thanh vien 3 | `feature/admin-tickets-utilities` | `/admin/tickets`, `/admin/utilities` |
| Thanh vien 4 | `feature/admin-reports-tenants` | `/admin/reports`, `/admin/tenants` |

## Quy Tac Code Backend

1. Moi nguoi chi code backend cua trang minh phu trach.
2. Khong sua giao dien cua trang nguoi khac neu khong thong bao truoc.
3. Neu can dung chung entity/service/repository, thong nhat ten package truoc khi code.
4. Code xong thi push branch cua minh len GitHub.
5. Nhom truong merge tung branch vao branch chinh sau khi test.

## Goi Y Cau Truc Package Backend

Nen giu cau truc theo domain:

```text
src/main/java/vn/glassliving/admin/page/properties/
src/main/java/vn/glassliving/admin/page/rooms/
src/main/java/vn/glassliving/property/
src/main/java/vn/glassliving/room/
```

Khong nen dua tat ca entity/service/repository vao chung mot thu muc theo ten trang, vi nhieu trang se dung chung du lieu.

## Lenh Git Goi Y

Lay branch nen:

```bash
git checkout frontend-base
```

Tao branch cua anh:

```bash
git checkout -b feature/admin-properties-rooms
```

Sau khi code xong:

```bash
git add .
git commit -m "Implement admin properties and rooms backend"
git push -u origin feature/admin-properties-rooms
```
