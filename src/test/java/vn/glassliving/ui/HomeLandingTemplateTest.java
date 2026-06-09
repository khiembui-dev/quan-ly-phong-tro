package vn.glassliving.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HomeLandingTemplateTest {

    @Test
    void homeLandingDoesNotRenderSearchBarOrRoomTypeFilters() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/customer/home.html"));

        assertThat(source).doesNotContain("glass-filter");
        assertThat(source).doesNotContain("href=\"/?type=STUDIO\"");
        assertThat(source).doesNotContain("href=\"/?type=DOUBLE\"");
        assertThat(source).doesNotContain("href=\"/?type=PENTHOUSE\"");
        assertThat(source).doesNotContain("href=\"/?petAllowed=true\"");
    }

    @Test
    void homeLandingHasLoginCtaForMoreRoomsAndBottomSpacing() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/customer/home.html"));

        assertThat(source).contains("#{home.rooms.moreLogin}");
        assertThat(source).contains("pb-24");
        assertThat(source).contains("sm:pb-32");
    }

    @Test
    void districtCardsUseHighContrastTextAndLinkToBookingFilters() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/customer/home.html"));

        assertThat(source).contains("href=\"/customer/booking?district=Qu%E1%BA%ADn+1\"");
        assertThat(source).contains("href=\"/customer/booking?district=Qu%E1%BA%ADn+3\"");
        assertThat(source).contains("text-white drop-shadow");
    }

    @Test
    void roomCardImagesHaveClientSideFallback() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/fragments/room-card.html"));

        assertThat(source).contains("data-room-cover");
        assertThat(source).contains("this.hidden=true");
        assertThat(source).contains("data-room-image-fallback");
    }

    @Test
    void homeLandingUsesMessageKeysForEnglishLanguageSwitch() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/customer/home.html"));

        assertThat(source)
                .contains("#{home.hero.eyebrow}")
                .contains("#{home.hero.titlePrefix}")
                .contains("#{home.rooms.title}")
                .contains("#{home.why.title}")
                .contains("#{home.districts.title}")
                .contains("#{home.testimonials.title}")
                .contains("#{home.stats.roomsListed}");

        assertThat(source)
                .doesNotContain(">Phòng đang còn trống<")
                .doesNotContain(">Khám phá theo quận<")
                .doesNotContain(">Hành trình tìm tổ ấm<");
    }

    @Test
    void roomCardUsesMessageKeysForLandingLabels() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/fragments/room-card.html"));

        assertThat(source)
                .contains("#{rooms.status.available}")
                .contains("#{rooms.type.boarding}")
                .contains("#{roomCard.balcony}")
                .contains("#{roomCard.perMonth}");
    }
}
