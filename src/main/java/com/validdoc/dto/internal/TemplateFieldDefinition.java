package com.validdoc.dto.internal;

public class TemplateFieldDefinition {

    private String label;
    private String type; // "TEXT" | "INK_ZONE" — opsiyonel, boşsa label'a göre otomatik karar verilir
    private double x;
    private double y;
    private double w;
    private double h;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getW() { return w; }
    public void setW(double w) { this.w = w; }
    public double getH() { return h; }
    public void setH(double h) { this.h = h; }
}