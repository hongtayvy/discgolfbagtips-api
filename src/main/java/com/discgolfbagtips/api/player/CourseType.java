package com.discgolfbagtips.api.player;

public enum CourseType {

    WOODED("tight wooded courses",
            "wooded golf rewards low-speed control, straight tunnel lines and discs that finish predictably "
                    + "in a small window; raw distance is rarely the constraint"),
    OPEN("wide open courses",
            "open golf rewards distance and wind resistance; high-glide drivers and a dependable overstable "
                    + "finisher matter more than tunnel control"),
    MIXED("a mix of wooded and open holes",
            "mixed courses reward a balanced bag with no missing stability class in the midrange and fairway slots");

    private final String description;
    private final String guidance;

    CourseType(String description, String guidance) {
        this.description = description;
        this.guidance = guidance;
    }

    public String description() {
        return description;
    }

    public String guidance() {
        return guidance;
    }
}
