package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FinancialCalculator
import com.example.data.GrowMintRepository
import com.example.model.*
import com.example.util.ScoreCardRenderer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharingAndReferralTest {

    private lateinit var context: Context
    private lateinit var repository: GrowMintRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("growmint_anonymous_referral_counts", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repository = GrowMintRepository(context)
        repository.resetAllData()
    }

    @Test
    fun scoreCardLabels_matchCodeThresholds() {
        assertEquals("Getting started", FinancialCalculator.calculateScoreCardLabel(0))
        assertEquals("Getting started", FinancialCalculator.calculateScoreCardLabel(44))
        assertEquals("Building", FinancialCalculator.calculateScoreCardLabel(45))
        assertEquals("Building", FinancialCalculator.calculateScoreCardLabel(69))
        assertEquals("Strong", FinancialCalculator.calculateScoreCardLabel(70))
        assertEquals("Strong", FinancialCalculator.calculateScoreCardLabel(84))
        assertEquals("Excellent", FinancialCalculator.calculateScoreCardLabel(85))
        assertEquals("Excellent", FinancialCalculator.calculateScoreCardLabel(100))
    }

    @Test
    fun componentBarLevels_mapToLowOkayGoodWithZeroNumbers() {
        assertEquals(ScoreCardBarLevel.LOW, FinancialCalculator.calculateComponentLevel(5, 30))
        assertEquals(ScoreCardBarLevel.OKAY, FinancialCalculator.calculateComponentLevel(15, 30))
        assertEquals(ScoreCardBarLevel.GOOD, FinancialCalculator.calculateComponentLevel(25, 30))
        assertEquals("Low", ScoreCardBarLevel.LOW.label)
        assertEquals("Okay", ScoreCardBarLevel.OKAY.label)
        assertEquals("Good", ScoreCardBarLevel.GOOD.label)
    }

    @Test
    fun buildPrivacySafeScoreCardData_enforcesPrivateDefaultsAndUnder18Lock() {
        val profile = UserProfile(
            currencyCode = "USD",
            monthlyIncome = 5000.0,
            currentBalance = 12345.0,
            isUnder18 = false
        )
        val report = FinancialCalculator.calculateMonthlyImprovementReport(
            profile = profile,
            envelopes = listOf(VirtualEnvelope("emergency", EnvelopeType.EMERGENCY_FUND, "Emergency", 3000.0)),
            transactions = emptyList(),
            previousSnapshot = null
        )

        // Default private state: nickname empty/off, bars off, no monthDelta because previousSnapshot is null
        val defaultCard = FinancialCalculator.buildPrivacySafeScoreCardData(
            report = report,
            isUnder18 = false
        )
        assertEquals("", defaultCard.nickname)
        assertFalse(defaultCard.showNickname)
        assertFalse(defaultCard.showComponentBars)
        assertFalse(defaultCard.showMonthlyChange)
        assertNull(defaultCard.monthDelta)
        assertTrue(defaultCard.componentBars.size <= 4)

        // Guided Mode (<18) forces private defaults even if toggles are passed as true
        val prevSnapshot = report.currentSnapshot.copy(monthKey = "2024-01", totalScore = report.currentScore - 6)
        val reportWithPrev = FinancialCalculator.calculateMonthlyImprovementReport(
            profile = profile,
            envelopes = listOf(VirtualEnvelope("emergency", EnvelopeType.EMERGENCY_FUND, "Emergency", 3000.0)),
            transactions = emptyList(),
            previousSnapshot = prevSnapshot
        )
        val under18Card = FinancialCalculator.buildPrivacySafeScoreCardData(
            report = reportWithPrev,
            isUnder18 = true,
            requestedShowMonthlyChange = true,
            requestedShowComponentBars = true,
            requestedShowNickname = true,
            requestedNickname = "Alex"
        )
        assertFalse(under18Card.showMonthlyChange)
        assertFalse(under18Card.showComponentBars)
        assertFalse(under18Card.showNickname)
        assertEquals("", under18Card.nickname)
        assertEquals(6, under18Card.monthDelta)
    }

    @Test
    fun renderScoreCardBitmap_producesExact1080SquareForDayAndNight() {
        val cardDay = ScoreCardData(
            score = 78,
            scoreLabel = "Strong",
            monthDelta = 6,
            componentBars = listOf(
                ScoreCardComponentBar("Emergency Buffer", ScoreCardBarLevel.GOOD),
                ScoreCardComponentBar("Spending Control", ScoreCardBarLevel.OKAY),
                ScoreCardComponentBar("Savings Habit", ScoreCardBarLevel.GOOD),
                ScoreCardComponentBar("Debt Load", ScoreCardBarLevel.LOW)
            ),
            nickname = "MintBuilder",
            showMonthlyChange = true,
            showComponentBars = true,
            showNickname = true,
            style = ScoreCardThemeStyle.DAY
        )
        val dayBmp = ScoreCardRenderer.renderScoreCardBitmap(cardDay)
        assertEquals(1080, dayBmp.width)
        assertEquals(1080, dayBmp.height)

        val nightBmp = ScoreCardRenderer.renderScoreCardBitmap(cardDay.copy(style = ScoreCardThemeStyle.NIGHT))
        assertEquals(1080, nightBmp.width)
        assertEquals(1080, nightBmp.height)
    }

    @Test
    fun shareText_containsZeroMoneyAmountsOrPersonalDetails() {
        val card = ScoreCardData(
            score = 74,
            scoreLabel = "Strong",
            monthDelta = 5,
            componentBars = emptyList(),
            showMonthlyChange = true
        )
        val text = FinancialCalculator.buildPrivacySafeShareText(
            card = card,
            includeReferralLink = true,
            referralCode = "MINT94AB"
        )
        assertTrue(text.contains("74/100"))
        assertTrue(text.contains("Strong"))
        assertTrue(text.contains("+5 this month"))
        assertTrue(text.contains("?ref=MINT94AB"))
        assertTrue(text.contains("Educational tool, not financial advice."))
        assertFalse(text.contains("$"))
        assertFalse(text.contains("USD"))
        assertFalse(text.contains("@"))
    }

    @Test
    fun referralParsingAndAttribution_tracksCountsOnlyAndBlocksSelfAndDuplicates() {
        val deepLinkCode = FinancialCalculator.parseReferralCodeFromUriOrText(
            "https://ais-pre-wt4cpwje43gerrmnerzzuo-428422984777.asia-northeast1.run.app?ref=ab12cd34"
        )
        assertEquals("AB12CD34", deepLinkCode)

        val ownCode = repository.referralState.value.myReferralCode
        assertNotNull(ownCode)
        assertTrue(FinancialCalculator.isValidReferralCodeFormat(ownCode))

        // 1. Self-referral is blocked on setup completion
        repository.captureInboundReferralCode(ownCode)
        val selfResult = repository.completeOnboardingReferralAttribution()
        assertEquals(ReferralAttributionResult.SELF_REFERRAL_BLOCKED, selfResult)
        assertEquals(0, repository.getCountForReferralCode(ownCode!!))

        // 2. Valid friend referral increments {code, count} on setup completion
        val friendCode = "FRIEND88"
        repository.captureInboundReferralCode("growmint://invite?ref=$friendCode")
        val creditResult = repository.completeOnboardingReferralAttribution()
        assertEquals(ReferralAttributionResult.CREDITED, creditResult)
        assertEquals(1, repository.getCountForReferralCode(friendCode))

        // 3. Duplicate attribution on same device is blocked
        repository.captureInboundReferralCode("OTHER999")
        val dupResult = repository.completeOnboardingReferralAttribution()
        assertEquals(ReferralAttributionResult.DUPLICATE_BLOCKED, dupResult)
        assertEquals(0, repository.getCountForReferralCode("OTHER999"))
    }

    @Test
    fun settingsSharingControls_toggleOffAndDeleteCodeAndCount() {
        val initialCode = repository.referralState.value.myReferralCode
        assertNotNull(initialCode)
        assertTrue(repository.referralState.value.sharingEnabled)

        repository.setSharingEnabled(false)
        assertFalse(repository.referralState.value.sharingEnabled)

        repository.deleteReferralCodeAndCount()
        assertNull(repository.referralState.value.myReferralCode)
        assertEquals(0, repository.referralState.value.referralCount)
        assertEquals(0, repository.getCountForReferralCode(initialCode!!))
    }

    @Test
    fun milestoneBadges_reflectReferralsScoreAndStreak() {
        val unlockedAll = FinancialCalculator.calculateMilestoneBadges(
            referralCount = 3,
            currentScore = 75,
            monthlySnapshotsCount = 3
        )
        assertEquals(4, unlockedAll.size)
        assertTrue(unlockedAll.all { it.isUnlocked })
        assertEquals("First invite", unlockedAll[0].title)
        assertEquals("3 friends joined", unlockedAll[1].title)
        assertEquals("Score 70+", unlockedAll[2].title)
        assertEquals("3-month streak", unlockedAll[3].title)
    }
}
