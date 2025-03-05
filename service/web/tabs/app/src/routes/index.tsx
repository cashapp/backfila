import * as React from "react"
import { Route, Switch } from "react-router"
import {
  BackfillStatusContainer,
  CloneFormContainer,
  CreateFormContainer,
  HomeContainer,
  ServiceDetailsContainer,
  ServiceVariantsContainer,
  LayoutContainer
} from "../containers"

const routes = (
  <div>
    <Switch>
      <Route
        path="/app/services/:service/variants/:variant/create"
        component={CreateFormContainer}
      />
      {/* Maintain compatibility for services that do not need variant functionality */}
      <Route
        path="/app/services/:service/create"
        component={CreateFormContainer}
      />
      {/* Maintain compatibility for services that do not need variant functionality */}
      <Route
        path="/app/services/:service/variants/:variant/"
        component={ServiceDetailsContainer}
      />
      <Route
        path="/app/services/:service/variants/"
        component={ServiceVariantsContainer}
      />
      {/* Maintain compatibility for services that do not need variant functionality */}
      <Route
        path="/app/services/:service"
        component={ServiceDetailsContainer}
      />
      <Route path="/app/backfills/:id/clone" component={CloneFormContainer} />
      <Route path="/app/backfills/:id" component={BackfillStatusContainer} />
      <Route path="/_admin/app/" component={HomeContainer} />
      <Route path="/_tab/app/" component={HomeContainer} />
      <Route path="/app/ext/" component={LayoutContainer} />{" "}
      {/* namespace for externally provided tabs that will still have the navbar */}
      <Route path="/app/" component={HomeContainer} />
      {/* Do not include a Route without a path or it will display during on all tabs */}
    </Switch>
  </div>
)

export default routes
