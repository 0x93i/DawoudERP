package com.daoud.model;

/**
 * صف واحد في تقرير المصنع أو تقرير المورد (شاشة "التقارير").
 * القيم رقمية خام (من غير وحدة ملصوقة) عشان تتصدّر لإكسيل بسهولة،
 * والواجهة هي اللي بتتولى التنسيق والوحدة وقت العرض على الشاشة.
 */
public record ReportRow(String date, String notes, String partyName,
                        double grossWeight, double pct, double dedKg,
                        double price, double amount) {}
