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

/**
 * The Selenium driver's information.
 *
 * @param property the system property to set with the location of the driver executable
 * @param location the location of the driver's executable
 * @param options the options to provide to the driver upon construction
 */
data class Driver(val property: DriverProperty, val location: DriverLocation, val options: DriverOptions)

/**
 * @param file the driver file's location
 */
data class DriverLocation(val file: File)

/**
 * @param text the options to provide when constructing the driver proper
 */
data class DriverOptions(val text: String)

/**
 * @param text the name of the system property that is used to indicate the driver's executable file location
 */
data class DriverProperty(val text: String)

/**
 * @param text the value to use when inputting a post code whilst driving the website
 */
data class PostCodeSearchTerm(val text: String)

/**
 * @param url the starting url to use whilst automating the website
 */
data class StartUrl(val url: URL)

/**
 * @param text the value to use when inputting a street name whilst driving the website
 */
data class StreetNameSearchTerm(val text: String)

/**
 * An input gateway interface that is capable of scraping Stevenage council's website for the next upcoming service's
 * details.
 */
interface HtmlInputGateway : NextUpcomingInputGateway

/**
 * The errors that `HtmlInputGateway.notify(...)` may return, contained in a `Left(.)`
 */
sealed class HtmlInputGatewayException : Throwable() {
    object UnspecifiedError : HtmlInputGatewayException()
    data class UnableToParseHtmlServiceDate(val htmlText: String) : HtmlInputGatewayException()
    data class UnableToParseHtmlServiceType(val htmlText: String) : HtmlInputGatewayException()
}

typealias HtmlInputGatewayError = HtmlInputGatewayException

/**
 * Create and returns a `HtmlInputGateway`.
 *
 * @param startUrl the scraping processes starting url
 * @param streetNameSearchTerm the street name to search for
 * @param postCodeSearchTerm the post code to search for
 * @param driver the driver's details
 * @param htmlDriverWaitDurationSeconds the maximum time to wait between browser automating operations
 *
 * @constructor
 */
fun createHtmlInputGateway(
    startUrl: StartUrl,
    streetNameSearchTerm: StreetNameSearchTerm,
    postCodeSearchTerm: PostCodeSearchTerm,
    driver: Driver,
    htmlDriverWaitDurationSeconds: WaitDurationSeconds
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
        WebDriverCommander(driver, htmlDriverWaitDurationSeconds).use(block)

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
                id(streetNameTextEditIdAttributeValue).map { it.sendKeys(streetNameSearchTerm.text) }
                id(postCodeTextEditIdAttributeValue).map { it.sendKeys(postCodeSearchTerm.text) }
                x(searchButtonXPathSelectorText).map { it.click() }
                x("//td[text() = '${streetNameSearchTerm.text}']").map { it.click() }
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
