import {
  defaultTheme,
  color,
  environmentToColor,
  IEnvironmentToColorLookup,
  ITheme
} from "@misk/core"

const environmentColorLookup: IEnvironmentToColorLookup = {
  default: color.cadet,
  DEVELOPMENT: color.platinum,
  TESTING: color.purple,
  STAGING: color.green,
  PRODUCTION: color.red
}

export const backfilaRed = "#dd4837"

export const backfilaTheme: ITheme = {
  ...defaultTheme,
  environmentToColor: environmentToColor(environmentColorLookup),
  navbarBackground: backfilaRed,
  navbarText: color.white
}
