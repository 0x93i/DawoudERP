# برنامج عم داود ERP — التوثيق الكامل

**الإصدار الحالي:** 2.7  
**التقنيات:** Java 26 + JavaFX 21.0.2 + Supabase (PostgreSQL) + HikariCP  
**المستودع:** https://github.com/0x93i/DawoudERP

---

## 1. نظرة عامة

برنامج Desktop لإدارة تجارة الخردة والبلاستيك. بيغطي دورة الشغل كاملة: من شراء البضاعة من الموردين، تخزينها في المخازن، بيعها للمصانع، وحتى إدارة الفلوس والعمال.

### هيكل العمل

```
مورد ──► مخزن ──► مصنع
  │        │        │
  └────► خزنة ◄─────┘
         │
      عم داود
```

---

## 2. التقنيات والبنية

### Stack

| المكون | التقنية |
|--------|---------|
| اللغة | Java 26 |
| الواجهة | JavaFX 21.0.2 |
| قاعدة البيانات | Supabase (PostgreSQL) |
| Connection Pool | HikariCP 5.1.0 |
| Build | Maven |
| التوزيع | jpackage (MSI) |

### الاتصال بقاعدة البيانات

```
Session Pooler: aws-1-eu-central-1.pooler.supabase.com:5432
User: postgres.oamzyslkqnewmtrwnoep
Pool Size: 5 connections
```

### هيكل المشروع

```
src/main/java/com/daoud/
├── Main.java                    نقطة البداية + RTL
├── MainLauncher.java            للـ jpackage
├── UpdateChecker.java           التحديث التلقائي
│
├── db/
│   ├── DatabaseManager_online   HikariCP + Supabase
│   └── VaultHelper              مساعد الخزنة
│
├── dao/
│   ├── SupplierDAO              الموردين
│   ├── WarehouseDAO             المخازن
│   ├── FactoryDAO               المصانع
│   ├── WorkerDAO                العمال
│   └── CustodyDAO               العهد (قديم)
│
├── model/
│   ├── Supplier(id, name, phone, sector, floorAmount, floorDate)
│   ├── Warehouse(id, name, managerUserId, managerName)
│   ├── Factory(id, name)
│   └── Worker(id, name, phone, warehouseId, dailyWage)
│
└── ui/
    ├── LoginScreen              تسجيل الدخول
    ├── MainLayout               الهيكل (Sidebar + Topbar)
    ├── DashboardContent         لوحة التحكم
    ├── SuppliersContent         قائمة الموردين
    ├── SupplierDetailContent    حساب المورد
    ├── WarehousesContent        قائمة المخازن
    ├── WarehouseDetailContent   تفاصيل المخزن
    ├── FactoriesContent         قائمة المصانع
    ├── FactoryDetailContent     حساب المصنع
    ├── WorkersContent           قائمة العمال
    ├── WorkerDetailContent      حساب العامل
    ├── ShipmentsContent         الشحنات
    ├── VaultsContent            قائمة الخزن
    ├── VaultContent             تفاصيل الخزنة
    ├── UsersContent             إدارة المستخدمين
    └── DialogHelper             مساعد النوافذ
```

---

## 3. قاعدة البيانات

### الجداول الأساسية

**users** — المستخدمين
```sql
id, username, password_hash, role
role: 'admin' | 'warehouse_manager'
```

**warehouses** — المخازن
```sql
id, name, manager_user_id
```

**suppliers** — الموردين
```sql
id, name, phone, sector, floor_amount, floor_date, created_by
```

**warehouse_suppliers** — ربط الموردين بالمخازن
```sql
warehouse_id, supplier_id
```

**factories** — المصانع
```sql
id, name, created_by
```

**workers** — العمال
```sql
id, name, phone, warehouse_id, daily_wage, created_by
```

### جداول المعاملات

**supplier_transactions** — بضاعة من المورد
```sql
id, supplier_id, transaction_date, gross_weight, 
deduction_kg, price_per_kg, recorded_by
```

**supplier_withdrawals** — سحب فلوس للمورد
```sql
id, supplier_id, amount, withdrawal_date, 
payment_method, notes, recorded_by
```

**supplier_settlements** — التسويات الأسبوعية
```sql
id, supplier_id, settlement_date, balance_before, 
amount_paid, new_floor, recorded_by
```

**warehouse_stock_entries** — دخول بضاعة للمخزن
```sql
id, warehouse_id, supplier_id, entry_date, 
total_weight, recorded_by
```

**warehouse_stock_exits** — خروج بضاعة من المخزن
```sql
id, warehouse_id, exit_date, weight_green, weight_colored,
weight_white, weight_waste, destination, exit_price, 
exit_value, recorded_by
```

**factory_shipments** — شحنات المصانع
```sql
id, factory_id, shipment_date, supplier_name, gross_weight,
deduction_pct, deduction_kg, net_weight, price_per_kg,
total_amount, source, purchase_total, cost_loading, cost_workers,
cost_fuel, cost_transport, cost_other, net_profit, recorded_by

source: 'supplier' | 'warehouse'
```

**factory_payments** — دفعات من المصانع
```sql
id, factory_id, payment_date, amount, notes, recorded_by
```

**worker_attendance** — حضور العمال
```sql
id, worker_id, work_date, daily_wage, recorded_by
```

**worker_withdrawals** — سحب العمال
```sql
id, worker_id, amount, withdrawal_date, 
payment_method, notes, recorded_by
```

**shipments** — الشحنات الكاملة
```sql
id, shipment_number, supplier_id, factory_id, shipment_date,
gross_weight, deduction_kg, net_weight, price_per_kg, total_amount,
purchase_price_per_kg, purchase_total,
factory_gross_weight, factory_deduction_kg, factory_net_weight,
sale_price_per_kg, sale_total,
cost_loading, cost_workers, cost_fuel, cost_transport, cost_other,
gross_profit, net_profit, status, recorded_by

status: 'pending' | 'completed' | 'partial'
```

### جداول الخزنة

**vaults** — الخزن
```sql
id, name, owner_type, owner_id, created_at
owner_type: 'admin' | 'warehouse'
```

**vault_transactions** — معاملات الخزنة
```sql
id, vault_id, transaction_date, direction, payment_type,
amount, category, reference_type, reference_id, notes, recorded_by

direction: 'in' | 'out'
payment_type: 'cash' | 'bank' | 'wallet' | 'check'
```

---

## 4. الصلاحيات

### Admin (عم داود)
- يشوف كل المخازن والموردين والعمال
- يضيف/يحذف مخازن ومستخدمين
- يتحكم في الخزنة الرئيسية وخزن المخازن
- يعمل تحويلات بين الخزن
- يعمل تسويات أسبوعية
- يشوف لوحة التحكم

### Warehouse Manager (مسؤول المخزن)
- يشوف مخزنه بس
- يشوف موردي مخزنه وعماله
- يسجل دخول وخروج بضاعة
- يسجل سحب فلوس للموردين والعمال
- يشوف خزنة مخزنه بس
- **مش** بيشوف لوحة التحكم ولا التسويات

---

## 5. الشاشات والوظائف

### لوحة التحكم (Admin فقط)

**Stats Cards:**
- إجمالي الداخل (هذا الأسبوع) بالطن
- إجمالي الخارج للمصانع بالطن
- رصيد المخازن الحالي بالطن
- مستحقات من المصانع بالجنيه

**Cards:**
- آخر 8 معاملات (بضاعة / سحب مورد / شحنة مصنع)
- أرصدة الموردين (مدين / دائن)

---

### الموردين

**قائمة الموردين:**
- جدول: الاسم، التليفون، القطاع، الأرضية، النوع
- **فلاتر:** الكل | مخازن | عام | + فلتر لكل مخزن على حدة
- **Tag:** 🏭 اسم المخزن أو 🌍 مورد عام
- إضافة مورد جديد مع اختيار المخزن (اختياري)

**حساب المورد:**
- معلومات: التليفون، الأرضية، الرصيد الحالي
- **تسجيل بضاعة:** الوزن الإجمالي + نسبة الخصم % + سعر الكيلو
  - كمية الخصم والوزن الصافي بيتحسبوا تلقائي
- **سحب فلوس:** المبلغ + طريقة الدفع + ملاحظة
  - بيتسجل في خزنة المخزن تلقائي (خروج)
- **تسوية أسبوعية** (Admin فقط)
- **سجل المعاملات:** جدول مع فلاتر + زر تعديل + زر حذف

**معادلة الرصيد:**
```
الرصيد = الأرضية − إجمالي البضاعة + إجمالي السحوبات
موجب = المورد مدين لعم داود
سالب = عم داود مدين للمورد
```

---

### المخازن

**قائمة المخازن:**
- إضافة مخزن + اختيار المسؤول
- **عند الإضافة:** بيتعمل خزنة للمخزن تلقائي
- **عند الحذف:** بتتحذف الخزنة المرتبطة
- إدارة موردي المخزن

**تفاصيل المخزن:**

**إجمالي البضاعة:**
- إجمالي الداخل / الخارج / المتبقي بالطن
- تفصيل بالأنواع: أخضر، ألوان، أبيض، زبالة

**تسجيل دخول بضاعة:**
- اختيار المورد (+ زر إضافة مورد جديد)
- الوزن الإجمالي + نسبة الخصم % + سعر الكيلو
- بيتسجل في `warehouse_stock_entries` و `supplier_transactions`

**تسجيل خروج بضاعة:**
- الكميات بالأنواع الأربعة
- سعر الشراء / كيلو
- **الوجهة:** عم داود أو مصنع (من قائمة)
- لو مصنع: بيفتح dialog لبيانات الشحنة الكاملة

**Dialog الشحنة للمصنع:**
- الوزن الإجمالي عند المصنع
- نسبة خصم المصنع %
- سعر البيع / كيلو
- صافي وزن المصنع + إجمالي البيع (تلقائي)
- مجمل الربح = إجمالي البيع − قيمة الشراء

---

### المصانع

**قائمة المصانع:**
- جدول بكل المصانع + الرصيد

**حساب المصنع:**
- الرصيد: المصنع مدين لعم داود / العكس
- **تسجيل شحنة:** المورد + الوزن + نسبة الخصم % + السعر
- **استلام دفعة:** المبلغ + طريقة الدفع + ملاحظة
  - بيتسجل في خزنة عم داود تلقائي (دخول)
- **سجل المعاملات:** التاريخ، النوع، اسم المورد، الوزن الصافي، نسبة الخصم، كمية الخصم، سعر الكيلو، المبلغ + تعديل + حذف

---

### العمال

**قائمة العمال:**
- اختيار المخزن (أو "عمال حرة")
- إضافة عامل + تليفون + أجر يومي
- **تسجيل حضور جماعي** بـ checkboxes

**حساب العامل:**
- ملخص: أيام العمل، إجمالي الأجر، المسحوب، المتبقي
- **تسجيل يوم عمل** بأجر محدد
- **سحب فلوس** + طريقة الدفع
  - بيتسجل في خزنة المخزن تلقائي (خروج)
- **تسوية** (Admin فقط)
- **سجل المعاملات** مع فلاتر + تعديل + حذف

---

### الشحنات

**جدول الشحنات:**
- رقم الشحنة، المورد، المصنع، الوزن الصافي، سعر الكيلو، الإجمالي، صافي الربح
- زر **تفاصيل** بيعرض كل البيانات

**شحنة جديدة:**
- **المورد:** بيظهر بس الموردين العامين (مش تابعين لمخزن)
- **المصنع:** من القائمة

**بيانات الشراء من المورد:**
- الوزن الإجمالي عند المورد
- نسبة خصم المورد %
- سعر الشراء / كيلو
- صافي وزن المورد + إجمالي الشراء (تلقائي)

**بيانات البيع للمصنع:**
- الوزن الإجمالي عند المصنع
- نسبة خصم المصنع %
- سعر البيع / كيلو
- صافي وزن المصنع + إجمالي البيع (تلقائي)

**مصاريف التشغيل:**
- تحميل، عمال، بنزين، نقل، أخرى

**النتائج:**
```
مجمل الربح = إجمالي البيع − إجمالي الشراء
صافي الربح = مجمل الربح − مصاريف التشغيل
```

**عند الحفظ بيتسجل في 3 أماكن:**
1. `shipments` — الشحنة الكاملة
2. `supplier_transactions` — حساب المورد
3. `factory_shipments` — حساب المصنع

---

### الخزنة

**قائمة الخزن:**
- خزنة عم داود (رئيسية)
- خزنة لكل مخزن
- كل خزنة بتعرض الإجمالي

**تحويل بين الخزن (Admin فقط):**
- من خزنة → إلى خزنة
- نوع الدفع + المبلغ + ملاحظة
- بيتسجل خروج من الأولى ودخول للتانية

**تفاصيل الخزنة:**

**الملخص:**
- الإجمالي الكلي
- تفصيل: كاش | بنك | محفظة | شيك

**تسجيل معاملة:**
- الاتجاه: دخول / خروج
- نوع الدفع: كاش / بنك / محفظة / شيك
- المبلغ + التصنيف + ملاحظة

**سجل المعاملات:**
- فلاتر: الكل | دخول | خروج | كاش | بنك | محفظة | شيك
- تعديل + حذف لكل معاملة

**الربط التلقائي:**

| العملية | الخزنة | الاتجاه |
|---------|--------|---------|
| سحب مورد | خزنة المخزن | خروج |
| تسوية مورد | خزنة المخزن | خروج |
| سحب عامل | خزنة المخزن | خروج |
| دفعة من مصنع | خزنة عم داود | دخول |
| تحويل بين خزن | الاتنين | خروج + دخول |

---

## 6. التحديث التلقائي

`UpdateChecker.java` بيشيك على GitHub Releases عند فتح البرنامج.

**الآلية:**
```
البرنامج يفتح
    ↓
يقرأ CURRENT_VERSION
    ↓
يجيب آخر release من GitHub API
    ↓
لو مختلف → dialog "تحديث متاح"
    ↓
المستخدم يضغط OK → يفتح صفحة التحميل
```

**API المستخدم:**
```
https://api.github.com/repos/0x93i/DawoudERP/releases/latest
```

---

## 7. عمل نسخة جديدة

### الخطوات بالترتيب

**1. عدل الكود وغير الإصدار**
```java
// في UpdateChecker.java
private static final String CURRENT_VERSION = "2.8";
```

**2. Build في IntelliJ**
```
Maven panel → Lifecycle → package
```

**3. الأوامر في CMD**
```cmd
cd C:\Users\xGeo\IdeaProjects\daoud-erp

copy target\daoud-app.jar target\libs\

rmdir /s /q target\runtime

jlink --module-path "%JAVA_HOME%\jmods;%USERPROFILE%\.m2\repository\org\openjfx\javafx-controls\21.0.2\javafx-controls-21.0.2-win.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-fxml\21.0.2\javafx-fxml-21.0.2-win.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-graphics\21.0.2\javafx-graphics-21.0.2-win.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-base\21.0.2\javafx-base-21.0.2-win.jar" --add-modules java.base,java.sql,java.desktop,java.naming,java.management,javafx.controls,javafx.fxml,javafx.graphics --output target\runtime

rmdir /s /q target\installer

jpackage --type msi --name "DawoudERP" --app-version "2.8" --runtime-image target\runtime --input target\libs --main-jar daoud-app.jar --main-class com.daoud.MainLauncher --dest target\installer --win-shortcut --win-menu --win-dir-chooser --win-upgrade-uuid "12345678-1234-1234-1234-123456789012"
```

**4. رفع على GitHub**
```cmd
git add .
git commit -m "v2.8 - description"
git push origin main
```

**5. عمل Release**
- `https://github.com/0x93i/DawoudERP/releases/new`
- Tag: `v2.8`
- ارفع الـ MSI من `target\installer\`
- Publish

---

## 8. ملاحظات تقنية

### RTL
في `Main.java` فيه Window listener بيطبق `RIGHT_TO_LEFT` على أي نافذة بتتفتح تلقائي.

### التواريخ
لازم `CURRENT_DATE` مش `date('now')` — الأخيرة SQLite مش PostgreSQL.

في الاستعلامات اللي بتقارن تواريخ محفوظة كـ TEXT:
```sql
WHERE entry_date::date >= CURRENT_DATE - INTERVAL '7 days'
```

### نسبة الخصم
كل الشاشات دلوقتي بتاخد **نسبة مئوية** وبتحسب الكيلو:
```java
double dedKg = gross * (pct / 100.0);
double net = gross - dedKg;
```

### ComboBox مع Objects
لازم `setCellFactory` و `setButtonCell` عشان يعرض الاسم:
```java
combo.setCellFactory(lv -> new ListCell<>() {
    @Override
    protected void updateItem(Supplier item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.getName());
    }
});
```

### العمال الحرة
`warehouse_id = NULL` مش `0` — لأن الـ foreign key مش بيقبل صفر:
```java
if (warehouseId == null) stmt.setNull(3, Types.INTEGER);
else stmt.setInt(3, warehouseId);
```

---

## 9. اللي لسه ناقص

- تقارير شهرية (أرباح، مشتريات، مبيعات)
- ميزان المراجعة / رأس المال
- تسجيل نقل بضاعة بين مخزن ومخزن
- صيانة ومصاريف العربية والمكبس
- الأرضيات كقائمة مستقلة
- تحديث تلقائي كامل (دلوقتي بيفتح المتصفح بس)

---

## 10. ملفات قديمة

فيه ملفات `*Screen.java` قديمة موجودة جنب الـ `*Content.java` الجديدة. مش بتتستخدم في الكود الحالي وممكن تتحذف:

```
CustodyScreen, FactoriesScreen, FactoryDetailScreen,
SuppliersScreen, SupplierDetailScreen, UsersScreen,
WarehousesScreen, WarehouseDetailScreen, WorkersScreen,
WorkerDetailScreen, MainScreen, HistoryTable
```
