import { simpleSelectorGet } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import {
    IDispatchProps,
    IState,
    mapDispatchToProps,
    mapStateToProps
} from "src/ducks"
import { LayoutContainer } from "."
import VariantsListComponent from "../components/VariantsListComponent";

class ServiceVariantsContainer extends React.Component<IState & IDispatchProps, IState> {
    private service: string = (this.props as any).match.params.service
    private serviceVariantsTag: string = `${this.service}::Variants`

    componentDidMount() {
        this.sendRequest()
    }

    sendRequest() {
      this.props.simpleNetworkGet(
        this.serviceVariantsTag,
        `/services/${this.service}/variants`
      )
    }

    render() {
        return (
            <LayoutContainer>
                <VariantsListComponent
                    data={simpleSelectorGet(
                        this.props.simpleNetwork,
                        [this.serviceVariantsTag, "data", "variants"],
                        []
                    )}
                    service={this.service}
                />
            </LayoutContainer>
        )
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(ServiceVariantsContainer)
