package com.silentlink.app.manager

object KoreanHolidays {

    // 고정 공휴일 (월, 일)
    private val fixed = setOf(
        1 to 1,   // 신정
        3 to 1,   // 삼일절
        5 to 5,   // 어린이날
        6 to 6,   // 현충일
        8 to 15,  // 광복절
        10 to 3,  // 개천절
        10 to 9,  // 한글날
        12 to 25  // 성탄절
    )

    // 음력 기반 공휴일 (연도별 사전 계산, 월 to 일)
    private val lunar: Map<Int, Set<Pair<Int, Int>>> = mapOf(
        2024 to setOf(
            2 to 9, 2 to 10, 2 to 11, 2 to 12,   // 설날 연휴
            5 to 15,                                // 부처님오신날
            9 to 16, 9 to 17, 9 to 18              // 추석 연휴
        ),
        2025 to setOf(
            1 to 28, 1 to 29, 1 to 30,             // 설날 연휴
            5 to 5,                                 // 부처님오신날 (어린이날 겹침)
            10 to 5, 10 to 6, 10 to 7              // 추석 연휴
        ),
        2026 to setOf(
            2 to 16, 2 to 17, 2 to 18, 2 to 19,   // 설날 연휴
            5 to 24,                                // 부처님오신날
            9 to 24, 9 to 25, 9 to 26, 9 to 27    // 추석 연휴
        ),
        2027 to setOf(
            2 to 6, 2 to 7, 2 to 8,               // 설날 연휴
            5 to 13,                                // 부처님오신날
            9 to 14, 9 to 15, 9 to 16              // 추석 연휴
        ),
        2028 to setOf(
            1 to 26, 1 to 27, 1 to 28,            // 설날 연휴
            5 to 2,                                 // 부처님오신날
            10 to 2, 10 to 3, 10 to 4              // 추석 연휴
        )
    )

    fun isHoliday(year: Int, month: Int, day: Int): Boolean {
        if (fixed.contains(month to day)) return true
        return lunar[year]?.contains(month to day) ?: false
    }
}
