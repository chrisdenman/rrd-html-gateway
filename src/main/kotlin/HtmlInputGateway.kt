package uk.co.ceilingcat.rrd.gateways.htmlinputgateway

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import uk.co.ceilingcat.rrd.entities.ServiceDate
import uk.co.ceilingcat.rrd.entities.ServiceDetails
import uk.co.ceilingcat.rrd.entities.ServiceType
import uk.co.ceilingcat.rrd.entities.createServiceDetails
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.HtmlInputGatewayException.UnableToParseHtmlServiceDate
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.HtmlInputGatewayException.UnableToParseHtmlServiceType
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.HtmlInputGatewayException.UnspecifiedError
import uk.co.ceilingcat.rrd.usecases.NextUpcomingInputGateway
import java.io.File
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class StreetName(val text: String)
data class PostCode(val text: String)
data class StartUrl(val url: URL)
data class Driver(val property: String, val location: File)

interface HtmlInputGateway : NextUpcomingInputGateway

sealed class HtmlInputGatewayException : Throwable() {
    object UnspecifiedError : HtmlInputGatewayException()
    data class UnableToParseHtmlServiceDate(val htmlText: String) : HtmlInputGatewayException()
    data class UnableToParseHtmlServiceType(val htmlText: String) : HtmlInputGatewayException()
}

typealias HtmlInputGatewayError = HtmlInputGatewayException

fun createHtmlInputGateway(
    startUrl: StartUrl,
    streetName: StreetName,
    postCode: PostCode,
    driver: Driver,
    waitDurationSeconds: WaitDurationSeconds
): HtmlInputGateway {

    val streetNameTextEditIdAttributeValue = "P1_C31_input"
    val postCodeTextEditIdAttributeValue = "P1_C105_input"
    val searchButtonXPathSelectorText = "//button[@type='button' and text() = 'Search']"
    val serviceDetailsResultsXPathSelectorText = "//table[@id='P1_C55_'][1]/tbody" +
        "/tr[contains(@class,'rowStyle')][1]/td[position() >1]"
    val htmlDateTimeFormatterPattern = "dd/MM/uuuu"

    val serviceDetailsResultsDateOffset = 0
    val serviceDetailsResultsTypeOffset = 1

    val htmlServiceTypeRecyclingDiscriminator = "recycling"
    val htmlServiceTypeRefuseDiscriminator = "refuse"

    fun <R> webDriving(block: WebDriverCommander.() -> Either<HtmlInputGatewayError, R>): Either<HtmlInputGatewayError, R> =
        WebDriverCommander(driver, waitDurationSeconds).use(block)

    fun parseServiceDate(serviceDateHtmlText: String): Either<HtmlInputGatewayError, ServiceDate> =
        try {
            LocalDate.parse(
                serviceDateHtmlText,
                DateTimeFormatter.ofPattern(htmlDateTimeFormatterPattern)
            ).right()
        } catch (t: Throwable) {
            UnableToParseHtmlServiceDate(serviceDateHtmlText).left()
        }

    fun parseServiceType(serviceTypeHtmlText: String): Either<HtmlInputGatewayError, ServiceType> =
        serviceTypeHtmlText.toLowerCase().let { normalisedHtml ->
            when {
                normalisedHtml.contains(htmlServiceTypeRefuseDiscriminator) -> ServiceType.REFUSE.right()
                normalisedHtml.contains(htmlServiceTypeRecyclingDiscriminator) -> ServiceType.RECYCLING.right()
                else -> UnableToParseHtmlServiceType(serviceTypeHtmlText).left()
            }
        }

    return object : HtmlInputGateway {
        override fun nextUpcoming(): Either<HtmlInputGatewayError, ServiceDetails> = webDriving {
            go(startUrl.url).flatMap {
                id(streetNameTextEditIdAttributeValue).map { it.sendKeys(streetName.text) }
                id(postCodeTextEditIdAttributeValue).map { it.sendKeys(postCode.text) }
                x(searchButtonXPathSelectorText).map { it.click() }
                x("//td[text() = '${streetName.text}']").map { it.click() }
                xs(serviceDetailsResultsXPathSelectorText).flatMap { serviceDateAndTypeTdElements ->
                    serviceDateAndTypeTdElements[serviceDetailsResultsDateOffset].text.let { serviceDateHtmlText ->
                        serviceDateAndTypeTdElements[serviceDetailsResultsTypeOffset].text.let { serviceTypeHtmlText ->
                            parseServiceType(serviceTypeHtmlText).flatMap { serviceType ->
                                parseServiceDate(serviceDateHtmlText).map { serviceDate ->
                                    createServiceDetails(serviceDate, serviceType)
                                }
                            }
                        }
                    }
                }
            }.mapLeft { UnspecifiedError }
        }
    }
}
