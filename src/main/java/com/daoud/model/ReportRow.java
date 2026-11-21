package com.daoud.model;

/**
 * صف واحد في تقرير المصنع أو تقرير المورد (شاشة "التقارير").
 * القيم رقمية خام (من غير وحدة ملصوقة) عشان تتصدّر لإكسيل بسهولة،
 * والواجهة هي اللي بتتولى التنسيق والوحدة وقت العرض على الشاشة.
 *
 * id: رقم الصف الأصلي في جدوله (supplier_transactions أو factory_shipments)
 * عشان تقدر الواجهة تحفظ تعديل الملاحظات على نفس السطر في قاعدة البيانات.
 *
 * partyName: في تقرير المصنع ده اسم المورد اللي ودّى البضاعة، وفي تقرير
 * المورد ده اسم المصنع اللي البضاعة راحتله (أو "—" لو البضاعة دي مسجلة
 * من غير شحنة/مصنع محدد).
 */
public record ReportRow(int id, String date, String notes, String partyName,
                        double grossWeight, double pct, double dedKg,
                        double price, double amount) {}
